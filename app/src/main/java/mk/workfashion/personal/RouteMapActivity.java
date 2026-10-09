package mk.workfashion.personal;

import android.app.Activity;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Circle;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RouteMapActivity extends Activity {
    public static final String EXTRA_FROM = "routeFrom";
    public static final String EXTRA_TO = "routeTo";
    public static final String EXTRA_LAT = "routeLat";
    public static final String EXTRA_LON = "routeLon";
    private static final String MAP_URL = "https://download.mapsforge.org/maps/v5/europe/macedonia.map";

    private FrameLayout root;
    private MapView mapView;
    private TextView status;
    private TextView title;
    private Button downloadButton;
    private Button roadButton;
    private MapFile offlineMap;
    private final List<Polyline> rawRouteLayers = new ArrayList<>();
    private String titleBaseText = "";
    private String from = "";
    private String to = "";
    private double focusLat = Double.NaN;
    private double focusLon = Double.NaN;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidGraphicFactory.createInstance(getApplication());

        from = safe(getIntent().getStringExtra(EXTRA_FROM));
        to = safe(getIntent().getStringExtra(EXTRA_TO));
        focusLat = getIntent().getDoubleExtra(EXTRA_LAT, Double.NaN);
        focusLon = getIntent().getDoubleExtra(EXTRA_LON, Double.NaN);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);

        File map = mapFile();
        if (map.exists() && map.length() > 1024 * 1024) showMap();
        else showDownloadScreen("Офлајн мапата за Македонија не е симната.");
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private File mapFile() {
        File dir = new File(getFilesDir(), "offline-maps");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "macedonia.map");
    }

    private void showDownloadScreen(String message) {
        root.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(48, 48, 48, 48);

        status = new TextView(this);
        status.setText(message + "\n\nМапата е околу 24 MB и се симнува еднаш. Потоа рутите работат без интернет.");
        status.setTextSize(17f);
        status.setTextColor(Color.rgb(21, 46, 53));
        status.setGravity(Gravity.CENTER);
        box.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        downloadButton = new Button(this);
        downloadButton.setText("Симни офлајн мапа");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, 30, 0, 0);
        box.addView(downloadButton, bp);
        downloadButton.setOnClickListener(v -> downloadMap());

        root.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void downloadMap() {
        if (downloadButton != null) downloadButton.setEnabled(false);
        if (status != null) status.setText("Се симнува офлајн мапата…");
        new Thread(() -> {
            File target = mapFile();
            File part = new File(target.getParentFile(), target.getName() + ".part");
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(MAP_URL).openConnection();
                con.setConnectTimeout(20000);
                con.setReadTimeout(30000);
                con.setRequestProperty("User-Agent", "WFAG/2.3 Android");
                con.connect();
                if (con.getResponseCode() / 100 != 2) throw new Exception("HTTP " + con.getResponseCode());

                long total = con.getContentLengthLong();
                long done = 0;
                try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    int lastPct = -1;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        done += n;
                        if (total > 0) {
                            int pct = (int) (done * 100 / total);
                            if (pct != lastPct && (pct % 2 == 0 || pct == 100)) {
                                lastPct = pct;
                                final int shown = pct;
                                runOnUiThread(() -> {
                                    if (status != null) status.setText("Се симнува офлајн мапата… " + shown + "%");
                                });
                            }
                        }
                    }
                    out.flush();
                }

                if (part.length() < 1024 * 1024) throw new Exception("Мапата е нецелосна.");
                if (target.exists() && !target.delete()) throw new Exception("Старата мапа не може да се замени.");
                if (!part.renameTo(target)) throw new Exception("Мапата не може да се зачува.");
                runOnUiThread(this::showMap);
            } catch (Exception e) {
                part.delete();
                final String msg = e.getMessage() == null ? "" : e.getMessage();
                runOnUiThread(() -> showDownloadScreen("Симнувањето не успеа. Провери интернет и пробај повторно." + (msg.isEmpty() ? "" : "\n" + msg)));
            } finally {
                if (con != null) con.disconnect();
            }
        }, "wfag-map-download").start();
    }

    private Paint routePaint() {
        Paint p = AndroidGraphicFactory.INSTANCE.createPaint();
        p.setColor(AndroidGraphicFactory.INSTANCE.createColor(org.mapsforge.core.graphics.Color.BLUE));
        p.setStrokeWidth(8f * getResources().getDisplayMetrics().density);
        p.setStyle(Style.STROKE);
        return p;
    }

    private Paint stopPaint() {
        Paint p = AndroidGraphicFactory.INSTANCE.createPaint();
        p.setColor(AndroidGraphicFactory.INSTANCE.createColor(org.mapsforge.core.graphics.Color.RED));
        p.setStyle(Style.FILL);
        return p;
    }

    private void showMap() {
        root.removeAllViews();

        mapView = new MapView(this);
        mapView.setClickable(true);
        mapView.getMapScaleBar().setVisible(true);
        mapView.setBuiltInZoomControls(true);
        root.addView(mapView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        try {
            TileCache cache = AndroidUtil.createTileCache(
                    this,
                    "wfag-offline-map-cache",
                    mapView.getModel().displayModel.getTileSize(),
                    1f,
                    mapView.getModel().frameBufferModel.getOverdrawFactor());

            if (offlineMap != null) {
                try { offlineMap.close(); } catch (Exception ignored) {}
            }
            offlineMap = new MapFile(mapFile());
            MapDataStore dataStore = offlineMap;
            TileRendererLayer base = new TileRendererLayer(
                    cache,
                    dataStore,
                    mapView.getModel().mapViewPosition,
                    AndroidGraphicFactory.INSTANCE);
            base.setXmlRenderTheme(MapsforgeThemes.DEFAULT);
            mapView.getLayerManager().getLayers().add(base);

            JSONObject routeState = new JSONObject(RouteTrackingService.stateJson(this));
            rawRouteLayers.clear();
            RouteBounds routeBounds = addRouteLayers(routeState);

            if (!Double.isNaN(focusLat) && !Double.isNaN(focusLon)) {
                mapView.setCenter(new LatLong(focusLat, focusLon));
                mapView.setZoomLevel((byte) 16);
            } else if (routeBounds.count > 0) {
                mapView.setCenter(new LatLong((routeBounds.minLat + routeBounds.maxLat) / 2d, (routeBounds.minLon + routeBounds.maxLon) / 2d));
                mapView.setZoomLevel(zoomForSpan(Math.max(routeBounds.maxLat - routeBounds.minLat, routeBounds.maxLon - routeBounds.minLon)));
            } else {
                mapView.setCenter(dataStore.boundingBox().getCenterPoint());
                mapView.setZoomLevel((byte) 8);
            }

            title = new TextView(this);
            String period = (!from.isEmpty() || !to.isEmpty()) ? "\n" + from + " – " + to : "";
            titleBaseText = "WFAG · GPS мапа" + period + "\n" + routeBounds.stopCount + " застанувања";
            title.setText(titleBaseText + (routeBounds.count > 1 ? "\nGPS линија ✓ · безбедна резерва" : ""));
            title.setTextSize(14f);
            title.setTextColor(Color.rgb(21, 46, 53));
            title.setBackgroundColor(Color.argb(235, 255, 255, 255));
            title.setPadding(24, 14, 24, 14);
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.gravity = Gravity.TOP | Gravity.START;
            tp.setMargins(20, 20, 20, 20);
            root.addView(title, tp);

            roadButton = new Button(this);
            roadButton.setText("Среди по пат");
            roadButton.setTextSize(13f);
            FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            gp.gravity = Gravity.TOP | Gravity.END;
            gp.setMargins(20, 170, 20, 20);
            root.addView(roadButton, gp);
            roadButton.setOnClickListener(v -> {
                try {
                    JSONObject latestState = new JSONObject(RouteTrackingService.stateJson(this));
                    startFreeRoadSnap(latestState);
                } catch (Exception e) {
                    Toast.makeText(this, "Не можам да ја прочитам GPS рутата.", Toast.LENGTH_LONG).show();
                }
            });

            if (routeBounds.count > 1) {
                List<List<LatLong>> cachedRoad = loadCompleteFreeRoadCache(routeState);
                if (!cachedRoad.isEmpty()) {
                    safeSwapToRoadRoutes(cachedRoad, "По пат ✓ · OSRM / OSM · зачувано");
                } else {
                    startFreeRoadSnap(routeState);
                }
            }
        } catch (Exception e) {
            showDownloadScreen("Офлајн мапата не може да се отвори. Симни ја повторно.");
        }
    }

    private RouteBounds addRouteLayers(JSONObject state) {
        RouteBounds bounds = new RouteBounds();
        try {
            JSONArray sessions = state.optJSONArray("sessions");
            if (sessions == null) return bounds;

            for (int i = 0; i < sessions.length(); i++) {
                JSONObject session = sessions.optJSONObject(i);
                if (session == null) continue;
                String date = dateOf(session.optLong("startedAt"));
                if (!from.isEmpty() && date.compareTo(from) < 0) continue;
                if (!to.isEmpty() && date.compareTo(to) > 0) continue;

                JSONArray points = session.optJSONArray("points");
                List<LatLong> route = new ArrayList<>();
                JSONObject previousPoint = null;
                if (points != null) {
                    for (int j = 0; j < points.length(); j++) {
                        JSONObject p = points.optJSONObject(j);
                        if (p == null) continue;
                        LatLong q = new LatLong(p.optDouble("lat"), p.optDouble("lon"));

                        if (previousPoint != null && shouldBreakRoute(previousPoint, p)) {
                            addRouteSegment(route);
                            route = new ArrayList<>();
                        }

                        route.add(q);
                        bounds.include(q);
                        previousPoint = p;
                    }
                }
                addRouteSegment(route);

                JSONArray stops = session.optJSONArray("stops");
                if (stops == null) continue;
                for (int j = 0; j < stops.length(); j++) {
                    JSONObject stop = stops.optJSONObject(j);
                    if (stop == null) continue;
                    LatLong q = new LatLong(stop.optDouble("lat"), stop.optDouble("lon"));
                    bounds.include(q);
                    bounds.stopCount++;
                    String name = stop.optString("name", "").trim();
                    if (name.isEmpty()) name = "Локација";
                    long start = stop.optLong("startAt");
                    long end = stop.optLong("endAt");
                    if (end <= 0) end = System.currentTimeMillis();
                    final String stopText = name + "\n" + clock(start) + " – " + clock(end) + " · " + duration(start, end);

                    Circle circle = new Circle(q, 35, stopPaint(), null) {
                        @Override public boolean onTap(LatLong tapLatLong, Point layerXY, Point tapXY) {
                            if (contains(layerXY, tapXY, tapLatLong.latitude, mapView)) {
                                Toast.makeText(RouteMapActivity.this, stopText, Toast.LENGTH_LONG).show();
                                return true;
                            }
                            return false;
                        }
                    };
                    mapView.getLayerManager().getLayers().add(circle);
                }
            }
        } catch (Exception ignored) {}
        return bounds;
    }

    private void addRouteSegment(List<LatLong> route) {
        if (route == null || route.size() < 2) return;
        Polyline line = new Polyline(routePaint(), AndroidGraphicFactory.INSTANCE);
        line.setPoints(route);
        mapView.getLayerManager().getLayers().add(line);
        rawRouteLayers.add(line);
    }

    private int selectedRouteSessionCount(JSONObject state) {
        int count = 0;
        JSONArray sessions = state.optJSONArray("sessions");
        if (sessions == null) return 0;
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject session = sessions.optJSONObject(i);
            if (session == null || !sessionInRange(session)) continue;
            JSONArray points = session.optJSONArray("points");
            if (points != null && points.length() >= 2) count++;
        }
        return count;
    }

    private List<List<LatLong>> loadCompleteFreeRoadCache(JSONObject state) {
        List<List<LatLong>> routes = new ArrayList<>();
        int expected = selectedRouteSessionCount(state);
        if (expected <= 0) return routes;

        JSONArray sessions = state.optJSONArray("sessions");
        if (sessions == null) return new ArrayList<>();

        for (int i = 0; i < sessions.length(); i++) {
            JSONObject session = sessions.optJSONObject(i);
            if (session == null || !sessionInRange(session)) continue;
            JSONArray points = session.optJSONArray("points");
            if (points == null || points.length() < 2) continue;

            FreeRoadSnapper.Result result = FreeRoadSnapper.loadCached(this, session);
            if (result == null || !result.ok()) return new ArrayList<>();
            routes.add(result.route);
        }

        return routes.size() == expected ? routes : new ArrayList<>();
    }

    private void startFreeRoadSnap(JSONObject state) {
        if (title != null) title.setText(titleBaseText + "\nСе мести рутата по пат…");
        if (roadButton != null) roadButton.setEnabled(false);

        new Thread(() -> {
            List<List<LatLong>> routes = new ArrayList<>();
            String error = "";
            int expected = selectedRouteSessionCount(state);

            try {
                JSONArray sessions = state.optJSONArray("sessions");
                if (sessions != null) {
                    for (int i = 0; i < sessions.length(); i++) {
                        JSONObject session = sessions.optJSONObject(i);
                        if (session == null || !sessionInRange(session)) continue;
                        JSONArray points = session.optJSONArray("points");
                        if (points == null || points.length() < 2) continue;

                        FreeRoadSnapper.Result result = FreeRoadSnapper.snap(
                                RouteMapActivity.this, session);
                        if (result == null || !result.ok()) {
                            error = result == null
                                    ? "Бесплатниот патен сервис не врати рута."
                                    : result.error;
                            routes.clear();
                            break;
                        }
                        routes.add(result.route);
                    }
                }
            } catch (Exception e) {
                error = e.getMessage() == null
                        ? "Бесплатниот патен сервис моментално не е достапен."
                        : e.getMessage();
                routes.clear();
            }

            final String shownError = error;
            final List<List<LatLong>> shownRoutes = routes;
            runOnUiThread(() -> {
                if (roadButton != null) roadButton.setEnabled(true);
                if (mapView == null || isFinishing()) return;

                if (expected > 0 && shownRoutes.size() == expected) {
                    safeSwapToRoadRoutes(shownRoutes, "По пат ✓ · OSRM / OSM · бесплатно");
                } else {
                    if (title != null) title.setText(titleBaseText + "\nGPS линија ✓ · безбедна резерва");
                    if (!shownError.isEmpty()) {
                        Toast.makeText(RouteMapActivity.this, shownError, Toast.LENGTH_LONG).show();
                    }
                }
            });
        }, "wfag-free-road-snap").start();
    }

    private void safeSwapToRoadRoutes(List<List<LatLong>> routes, String statusText) {
        if (routes == null || routes.isEmpty() || mapView == null) return;

        try {
            List<Polyline> newLayers = new ArrayList<>();
            int totalPoints = 0;

            for (List<LatLong> route : routes) {
                if (route == null || route.size() < 2) continue;
                totalPoints += route.size();
                Polyline line = new Polyline(routePaint(), AndroidGraphicFactory.INSTANCE);
                line.setPoints(route);
                mapView.getLayerManager().getLayers().add(line);
                newLayers.add(line);
            }

            if (newLayers.isEmpty() || totalPoints < 8) {
                for (Polyline line : newLayers) {
                    mapView.getLayerManager().getLayers().remove(line);
                }
                if (title != null) title.setText(titleBaseText + "\nGPS линија ✓ · безбедна резерва");
                return;
            }

            for (Polyline line : new ArrayList<>(rawRouteLayers)) {
                mapView.getLayerManager().getLayers().remove(line);
            }
            rawRouteLayers.clear();

            if (title != null) title.setText(titleBaseText + "\n" + statusText);
        } catch (Exception ignored) {
            if (title != null) title.setText(titleBaseText + "\nGPS линија ✓ · безбедна резерва");
        }
    }

    private boolean sessionInRange(JSONObject session) {
        String date = dateOf(session.optLong("startedAt"));
        if (!from.isEmpty() && date.compareTo(from) < 0) return false;
        if (!to.isEmpty() && date.compareTo(to) > 0) return false;
        return true;
    }

    private static boolean shouldBreakRoute(JSONObject a, JSONObject b) {
        long at = a.optLong("at", 0L);
        long bt = b.optLong("at", 0L);
        long dt = Math.max(1000L, bt - at);
        float[] r = new float[1];
        Location.distanceBetween(a.optDouble("lat"), a.optDouble("lon"), b.optDouble("lat"), b.optDouble("lon"), r);
        float d = r[0];
        float aAcc = (float) a.optDouble("accuracy", 30d);
        float bAcc = (float) b.optDouble("accuracy", 30d);
        float plausible = Math.max(180f, (dt / 1000f) * 70f + aAcc + bAcc);
        return d > plausible || (dt > 45000L && d > 250f);
    }

    private static byte zoomForSpan(double span) {
        if (span < 0.005) return 16;
        if (span < 0.015) return 14;
        if (span < 0.05) return 12;
        if (span < 0.15) return 10;
        if (span < 0.5) return 9;
        if (span < 1.5) return 8;
        return 7;
    }

    private static String dateOf(long ms) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(ms));
    }

    private static String clock(long ms) {
        if (ms <= 0) return "—";
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    private static String duration(long start, long end) {
        long min = Math.max(0, Math.round((end - start) / 60000.0));
        long h = min / 60;
        long m = min % 60;
        return h > 0 ? h + "ч " + m + "м" : m + " мин";
    }

    @Override protected void onDestroy() {
        if (mapView != null) {
            mapView.destroyAll();
            mapView = null;
        }
        if (offlineMap != null) {
            try { offlineMap.close(); } catch (Exception ignored) {}
            offlineMap = null;
        }
        AndroidGraphicFactory.clearResourceMemoryCache();
        super.onDestroy();
    }

    private static class RouteBounds {
        int count = 0;
        int stopCount = 0;
        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;

        void include(LatLong p) {
            if (p == null) return;
            count++;
            minLat = Math.min(minLat, p.latitude);
            maxLat = Math.max(maxLat, p.latitude);
            minLon = Math.min(minLon, p.longitude);
            maxLon = Math.max(maxLon, p.longitude);
        }
    }
}
