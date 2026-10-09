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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Free road map-matching for WFAG using the public Project OSRM demo service.
 *
 * No account, API key or card is required. Successful results are cached
 * locally so the route can later be viewed offline. The raw GPS route is kept
 * by RouteMapActivity until a complete road result passes validation.
 *
 * The public OSRM server is best-effort/fair-use, so failure always falls back
 * to the locally recorded GPS trace.
 */
final class FreeRoadSnapper {
    private static final int CACHE_VERSION = 1;
    private static final int MAX_POINTS_PER_REQUEST = 75;
    private static final int MAX_REQUESTS = 40;
    private static final double SAMPLE_SPACING_M = 500d;
    private static final double MAX_ENDPOINT_GAP_M = 1200d;
    private static final double MAX_COVERAGE_GAP_M = 1200d;
    private static final String BASE_URL = "https://router.project-osrm.org";

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

    private FreeRoadSnapper() {}

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

    static Result snap(Context context, JSONObject session) {
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

            List<TracePoint> controls = sample(raw);
            if (controls.size() < 2) {
                return new Result(Collections.emptyList(), "Нема доволно валидни GPS точки.", false);
            }

            List<LatLong> snapped = new ArrayList<>();
            int start = 0;
            int requests = 0;

            while (start < controls.size() - 1 && requests < MAX_REQUESTS) {
                int end = Math.min(controls.size(), start + MAX_POINTS_PER_REQUEST);
                List<TracePoint> chunk = controls.subList(start, end);

                List<LatLong> part = requestMatch(chunk);
                if (part.size() < 2) {
                    // Match can fail on very sparse historical data. Route via
                    // the recorded control points is the safe free fallback.
                    part = requestRoute(chunk);
                }
                if (part.size() < 2) {
                    return new Result(Collections.emptyList(), "Бесплатниот патен сервис не ја врати целата рута.", false);
                }

                appendDedup(snapped, part);
                requests++;

                if (end >= controls.size()) break;
                start = end - 2;
            }

            if (start < controls.size() - 1) {
                return new Result(Collections.emptyList(), "Рутата е предолга за едно обработување.", false);
            }

            if (!validate(raw, snapped)) {
                return new Result(Collections.emptyList(), "Патната линија не ја покрива безбедно целата GPS рута.", false);
            }

            writeCache(context, session.optString("id", "route"), signature, snapped);
            return new Result(snapped, "", false);
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = "Бесплатниот патен сервис моментално не е достапен.";
            }
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

    private static List<TracePoint> sample(List<TracePoint> raw) {
        List<TracePoint> out = new ArrayList<>();
        if (raw.isEmpty()) return out;

        TracePoint lastAdded = raw.get(0);
        out.add(lastAdded);

        for (int i = 1; i < raw.size() - 1; i++) {
            TracePoint p = raw.get(i);
            double d = distance(lastAdded.lat, lastAdded.lon, p.lat, p.lon);
            long dt = p.at > 0 && lastAdded.at > 0 ? p.at - lastAdded.at : 0L;

            if (d >= SAMPLE_SPACING_M || dt >= 60000L) {
                out.add(p);
                lastAdded = p;
            }
        }

        TracePoint last = raw.get(raw.size() - 1);
        if (out.get(out.size() - 1) != last) out.add(last);
        return out;
    }

    private static List<LatLong> requestMatch(List<TracePoint> points) throws Exception {
        String coords = coordinates(points);
        String url = BASE_URL + "/match/v1/driving/" + coords
                + "?geometries=geojson&overview=full&steps=false&gaps=ignore&tidy=true";
        JSONObject root = getJson(url);

        if (!"Ok".equalsIgnoreCase(root.optString("code"))) return Collections.emptyList();

        JSONArray matchings = root.optJSONArray("matchings");
        if (matchings == null || matchings.length() == 0) return Collections.emptyList();

        List<LatLong> out = new ArrayList<>();
        for (int i = 0; i < matchings.length(); i++) {
            JSONObject matching = matchings.optJSONObject(i);
            if (matching == null) continue;
            JSONObject geometry = matching.optJSONObject("geometry");
            appendGeoJsonGeometry(out, geometry);
        }
        return out;
    }

    private static List<LatLong> requestRoute(List<TracePoint> points) throws Exception {
        String coords = coordinates(points);
        String url = BASE_URL + "/route/v1/driving/" + coords
                + "?geometries=geojson&overview=full&steps=false&continue_straight=false";
        JSONObject root = getJson(url);

        if (!"Ok".equalsIgnoreCase(root.optString("code"))) return Collections.emptyList();

        JSONArray routes = root.optJSONArray("routes");
        if (routes == null || routes.length() == 0) return Collections.emptyList();

        JSONObject route = routes.optJSONObject(0);
        if (route == null) return Collections.emptyList();

        List<LatLong> out = new ArrayList<>();
        appendGeoJsonGeometry(out, route.optJSONObject("geometry"));
        return out;
    }

    private static JSONObject getJson(String urlText) throws Exception {
        HttpURLConnection con = (HttpURLConnection)new URL(urlText).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);
        con.setRequestMethod("GET");
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("User-Agent", "WFAG/2.3 Android - personal route history");
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

        if (code < 200 || code >= 300) {
            throw new Exception("OSRM HTTP " + code);
        }

        return new JSONObject(body.toString());
    }

    private static String coordinates(List<TracePoint> points) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) out.append(';');
            TracePoint p = points.get(i);
            out.append(String.format(Locale.US, "%.6f,%.6f", p.lon, p.lat));
        }
        return out.toString();
    }

    private static void appendGeoJsonGeometry(List<LatLong> target, JSONObject geometry) {
        if (geometry == null) return;
        JSONArray coordinates = geometry.optJSONArray("coordinates");
        if (coordinates == null) return;

        for (int i = 0; i < coordinates.length(); i++) {
            JSONArray pair = coordinates.optJSONArray(i);
            if (pair == null || pair.length() < 2) continue;
            double lon = pair.optDouble(0, Double.NaN);
            double lat = pair.optDouble(1, Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon)) continue;

            LatLong p = new LatLong(lat, lon);
            if (!target.isEmpty()) {
                LatLong last = target.get(target.size() - 1);
                if (distance(last.latitude, last.longitude, p.latitude, p.longitude) < 1d) continue;
            }
            target.add(p);
        }
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

        TracePoint first = raw.get(0);
        TracePoint last = raw.get(raw.size() - 1);
        LatLong roadFirst = road.get(0);
        LatLong roadLast = road.get(road.size() - 1);

        if (distance(first.lat, first.lon, roadFirst.latitude, roadFirst.longitude) > MAX_ENDPOINT_GAP_M) return false;
        if (distance(last.lat, last.lon, roadLast.latitude, roadLast.longitude) > MAX_ENDPOINT_GAP_M) return false;

        int samples = Math.min(20, raw.size());
        int covered = 0;

        for (int s = 0; s < samples; s++) {
            int idx = samples == 1 ? 0
                    : (int)Math.round((raw.size() - 1) * (s / (double)(samples - 1)));
            TracePoint p = raw.get(idx);
            double best = Double.POSITIVE_INFINITY;

            for (LatLong rp : road) {
                double d = distance(p.lat, p.lon, rp.latitude, rp.longitude);
                if (d < best) best = d;
                if (best <= MAX_COVERAGE_GAP_M) break;
            }

            if (best <= MAX_COVERAGE_GAP_M) covered++;
        }

        return covered >= Math.max(3, (int)Math.ceil(samples * 0.90d));
    }

    private static String signature(JSONObject session, List<TracePoint> raw) {
        TracePoint last = raw.get(raw.size() - 1);
        return CACHE_VERSION + "-" + raw.size() + "-" + last.at + "-" + session.optLong("endedAt", 0L);
    }

    private static File cacheFile(Context context, String sessionId) {
        File dir = new File(context.getFilesDir(), "free-road-snap-cache");
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
