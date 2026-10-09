package mk.workfashion.personal;

import android.content.Context;
import android.location.Location;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mapsforge.core.model.LatLong;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Google Roads API based map matching.
 *
 * The API key is supplied at runtime by the user and is never committed to the
 * repository. Successful road geometry is cached locally per route so it can
 * be shown later without internet.
 */
final class GoogleRoadSnapper {
    private static final int CACHE_VERSION = 1;
    private static final int MAX_API_POINTS = 100;
    private static final double TARGET_SPACING_M = 220d;
    private static final double MAX_GAP_TO_DENSIFY_M = 5000d;
    private static final int MAX_TOTAL_CONTROL_POINTS = 2500;
    private static final int MAX_REQUESTS = 40;
    private static final double MAX_ENDPOINT_GAP_M = 700d;
    private static final double MAX_COVERAGE_GAP_M = 700d;

    static final class Result {
        final List<LatLong> route;
        final String error;
        final boolean fromCache;

        Result(List<LatLong> route, String error, boolean fromCache) {
            this.route = route == null ? Collections.emptyList() : route;
            this.error = error == null ? "" : error;
            this.fromCache = fromCache;
        }

        boolean ok() { return route.size() >= 2; }
    }

    private static final class TracePoint {
        final double lat, lon;
        final long at;
        TracePoint(double lat, double lon, long at) {
            this.lat = lat;
            this.lon = lon;
            this.at = at;
        }
    }

    private GoogleRoadSnapper() {}

    static Result loadCached(Context context, JSONObject session) {
        try {
            List<TracePoint> raw = trace(session.optJSONArray("points"));
            if (raw.size() < 2) return new Result(Collections.emptyList(), "", true);
            String signature = signature(session, raw);
            List<LatLong> cached = readCache(context, session.optString("id", "route"), signature);
            if (cached != null && validate(raw, cached)) {
                return new Result(cached, "", true);
            }
        } catch (Exception ignored) {}
        return new Result(Collections.emptyList(), "", true);
    }

    static Result snap(Context context, JSONObject session, String apiKey) {
        if (apiKey == null || apiKey.trim().length() < 20) {
            return new Result(Collections.emptyList(), "Нема Google Roads API key.", false);
        }

        try {
            List<TracePoint> raw = trace(session.optJSONArray("points"));
            if (raw.size() < 2) {
                return new Result(Collections.emptyList(), "Нема доволно GPS точки.", false);
            }

            String signature = signature(session, raw);
            List<LatLong> cached = readCache(context, session.optString("id", "route"), signature);
            if (cached != null && validate(raw, cached)) {
                return new Result(cached, "", true);
            }

            List<TracePoint> controls = densify(raw);
            if (controls.size() < 2) {
                return new Result(Collections.emptyList(), "Нема доволно валидни точки за Google.", false);
            }

            List<LatLong> snapped = new ArrayList<>();
            int start = 0;
            int requests = 0;

            while (start < controls.size() - 1 && requests < MAX_REQUESTS) {
                int end = Math.min(controls.size(), start + MAX_API_POINTS);
                List<TracePoint> chunk = controls.subList(start, end);
                List<LatLong> part = requestChunk(chunk, apiKey.trim());

                if (part.size() < 2) {
                    return new Result(Collections.emptyList(), "Google не врати целосна патна линија.", false);
                }

                appendDedup(snapped, part);
                requests++;

                if (end >= controls.size()) break;
                start = end - 2; // overlap preserves continuity between calls
            }

            if (start < controls.size() - 1) {
                return new Result(Collections.emptyList(), "Рутата е предолга за едно Google обработување.", false);
            }

            if (!validate(raw, snapped)) {
                return new Result(Collections.emptyList(), "Google резултатот не ја покрива безбедно целата рута.", false);
            }

            writeCache(context, session.optString("id", "route"), signature, snapped);
            return new Result(snapped, "", false);
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || message.trim().isEmpty()) message = "Google Roads не е достапен.";
            return new Result(Collections.emptyList(), message, false);
        }
    }

    private static List<TracePoint> trace(JSONArray points) {
        List<TracePoint> out = new ArrayList<>();
        if (points == null) return out;

        TracePoint previous = null;
        for (int i = 0; i < points.length(); i++) {
            JSONObject p = points.optJSONObject(i);
            if (p == null) continue;

            double lat = p.optDouble("lat", Double.NaN);
            double lon = p.optDouble("lon", Double.NaN);
            double accuracy = p.optDouble("accuracy", 40d);
            long at = p.optLong("at", 0L);

            if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
            if (Math.abs(lat) > 90d || Math.abs(lon) > 180d) continue;
            if (accuracy > 180d) continue;

            TracePoint current = new TracePoint(lat, lon, at);
            if (previous != null) {
                double d = distance(previous.lat, previous.lon, current.lat, current.lon);
                long dt = Math.max(1000L, current.at - previous.at);
                double speed = d / (dt / 1000d);
                if (d > 450d && speed > 80d) continue;
                if (d < 2d && dt < 10000L) continue;
            }

            out.add(current);
            previous = current;
        }
        return out;
    }

    private static List<TracePoint> densify(List<TracePoint> raw) {
        List<TracePoint> out = new ArrayList<>();
        if (raw.isEmpty()) return out;

        out.add(raw.get(0));
        for (int i = 1; i < raw.size(); i++) {
            TracePoint a = raw.get(i - 1);
            TracePoint b = raw.get(i);
            double d = distance(a.lat, a.lon, b.lat, b.lon);

            if (d > MAX_GAP_TO_DENSIFY_M) {
                // Preserve the real sample but do not invent hundreds of
                // straight-line controls across a very large GPS outage.
                out.add(b);
            } else {
                int steps = Math.max(1, (int)Math.ceil(d / TARGET_SPACING_M));
                for (int s = 1; s <= steps; s++) {
                    double t = s / (double)steps;
                    double lat = a.lat + (b.lat - a.lat) * t;
                    double lon = a.lon + (b.lon - a.lon) * t;
                    long at = (a.at > 0 && b.at > 0)
                            ? a.at + Math.round((b.at - a.at) * t)
                            : 0L;
                    out.add(new TracePoint(lat, lon, at));
                    if (out.size() >= MAX_TOTAL_CONTROL_POINTS) return out;
                }
            }

            if (out.size() >= MAX_TOTAL_CONTROL_POINTS) return out;
        }
        return out;
    }

    private static List<LatLong> requestChunk(List<TracePoint> points, String apiKey) throws Exception {
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) path.append('|');
            TracePoint p = points.get(i);
            path.append(String.format(java.util.Locale.US, "%.6f,%.6f", p.lat, p.lon));
        }

        String url = "https://roads.googleapis.com/v1/snapToRoads?interpolate=true&path="
                + URLEncoder.encode(path.toString(), "UTF-8")
                + "&key=" + URLEncoder.encode(apiKey, "UTF-8");

        HttpURLConnection con = (HttpURLConnection)new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(25000);
        con.setRequestMethod("GET");
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("User-Agent", "WFAG/2.2 Android");
        con.connect();

        int code = con.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream(),
                StandardCharsets.UTF_8));

        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) body.append(line);
        reader.close();
        con.disconnect();

        JSONObject root = new JSONObject(body.toString());
        if (code < 200 || code >= 300) {
            JSONObject error = root.optJSONObject("error");
            String message = error == null ? ("Google HTTP " + code) : error.optString("message", "Google HTTP " + code);
            throw new Exception(message);
        }

        JSONArray snappedPoints = root.optJSONArray("snappedPoints");
        if (snappedPoints == null) return Collections.emptyList();

        List<LatLong> out = new ArrayList<>();
        for (int i = 0; i < snappedPoints.length(); i++) {
            JSONObject sp = snappedPoints.optJSONObject(i);
            if (sp == null) continue;
            JSONObject loc = sp.optJSONObject("location");
            if (loc == null) continue;
            double lat = loc.optDouble("latitude", Double.NaN);
            double lon = loc.optDouble("longitude", Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
            out.add(new LatLong(lat, lon));
        }
        return out;
    }

    private static void appendDedup(List<LatLong> target, List<LatLong> source) {
        for (LatLong p : source) {
            if (p == null) continue;
            if (!target.isEmpty()) {
                LatLong last = target.get(target.size() - 1);
                if (distance(last.latitude, last.longitude, p.latitude, p.longitude) < 1d) continue;
            }
            target.add(p);
        }
    }

    private static boolean validate(List<TracePoint> raw, List<LatLong> road) {
        if (raw == null || raw.size() < 2 || road == null || road.size() < 8) return false;

        double rawMeters = 0d;
        for (int i = 1; i < raw.size(); i++) {
            TracePoint a = raw.get(i - 1);
            TracePoint b = raw.get(i);
            double d = distance(a.lat, a.lon, b.lat, b.lon);
            if (d < 5000d) rawMeters += d;
        }

        double roadMeters = 0d;
        for (int i = 1; i < road.size(); i++) {
            LatLong a = road.get(i - 1);
            LatLong b = road.get(i);
            roadMeters += distance(a.latitude, a.longitude, b.latitude, b.longitude);
        }

        if (rawMeters < 300d || roadMeters < 300d) return false;
        if (roadMeters < rawMeters * 0.60d) return false;
        if (roadMeters > rawMeters * 2.50d + 5000d) return false;

        TracePoint first = raw.get(0);
        TracePoint last = raw.get(raw.size() - 1);
        LatLong roadFirst = road.get(0);
        LatLong roadLast = road.get(road.size() - 1);

        if (distance(first.lat, first.lon, roadFirst.latitude, roadFirst.longitude) > MAX_ENDPOINT_GAP_M) return false;
        if (distance(last.lat, last.lon, roadLast.latitude, roadLast.longitude) > MAX_ENDPOINT_GAP_M) return false;

        int samples = Math.min(20, raw.size());
        int covered = 0;
        for (int s = 0; s < samples; s++) {
            int idx = samples == 1 ? 0 : (int)Math.round((raw.size() - 1) * (s / (double)(samples - 1)));
            TracePoint p = raw.get(idx);
            double best = Double.POSITIVE_INFINITY;

            for (LatLong rp : road) {
                double d = distance(p.lat, p.lon, rp.latitude, rp.longitude);
                if (d < best) best = d;
                if (best <= MAX_COVERAGE_GAP_M) break;
            }

            if (best <= MAX_COVERAGE_GAP_M) covered++;
        }

        return covered >= Math.max(3, (int)Math.ceil(samples * 0.95d));
    }

    private static String signature(JSONObject session, List<TracePoint> raw) {
        TracePoint last = raw.get(raw.size() - 1);
        return CACHE_VERSION + "-" + raw.size() + "-" + last.at + "-" + session.optLong("endedAt", 0L);
    }

    private static File cacheFile(Context context, String sessionId) {
        File dir = new File(context.getFilesDir(), "google-road-snap-cache");
        if (!dir.exists()) dir.mkdirs();
        String safe = sessionId == null ? "route" : sessionId.replaceAll("[^A-Za-z0-9_.-]", "_");
        return new File(dir, safe + ".json");
    }

    private static List<LatLong> readCache(Context context, String sessionId, String signature) {
        File file = cacheFile(context, sessionId);
        if (!file.exists() || file.length() <= 0 || file.length() > 12 * 1024 * 1024) return null;

        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int)file.length()];
            int off = 0;
            int n;
            while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;

            JSONObject root = new JSONObject(new String(data, 0, off, StandardCharsets.UTF_8));
            if (!signature.equals(root.optString("signature"))) return null;

            JSONArray arr = root.optJSONArray("route");
            if (arr == null) return null;

            List<LatLong> route = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONArray pair = arr.optJSONArray(i);
                if (pair == null || pair.length() < 2) continue;
                route.add(new LatLong(pair.optDouble(0), pair.optDouble(1)));
            }
            return route;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void writeCache(Context context, String sessionId, String signature, List<LatLong> route) {
        try {
            JSONObject root = new JSONObject();
            root.put("signature", signature);

            JSONArray arr = new JSONArray();
            for (LatLong p : route) {
                JSONArray pair = new JSONArray();
                pair.put(p.latitude);
                pair.put(p.longitude);
                arr.put(pair);
            }
            root.put("route", arr);

            byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 12 * 1024 * 1024) return;

            try (FileOutputStream out = new FileOutputStream(cacheFile(context, sessionId), false)) {
                out.write(bytes);
                out.flush();
            }
        } catch (Exception ignored) {}
    }

    private static double distance(double aLat, double aLon, double bLat, double bLon) {
        float[] result = new float[1];
        Location.distanceBetween(aLat, aLon, bLat, bLon, result);
        return result[0];
    }
}
