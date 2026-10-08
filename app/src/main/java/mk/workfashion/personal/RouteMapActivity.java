package mk.workfashion.personal;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RouteMapActivity extends Activity implements OnMapReadyCallback {
    public static final String EXTRA_FROM = "routeFrom";
    public static final String EXTRA_TO = "routeTo";
    private static final String MAP_STATE = "wfag_map_state";
    private MapView mapView;
    private String from = "";
    private String to = "";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        from = getIntent().getStringExtra(EXTRA_FROM);
        to = getIntent().getStringExtra(EXTRA_TO);
        if (from == null) from = "";
        if (to == null) to = "";

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);

        if (!hasMapsKey()) {
            TextView missing = new TextView(this);
            missing.setText("Google Maps е подготвен, но недостига MAPS_API_KEY.\n\nДодај го клучот при build и мапата ќе се вклучи без други измени.");
            missing.setTextSize(18f);
            missing.setTextColor(Color.rgb(21,46,53));
            missing.setGravity(Gravity.CENTER);
            missing.setPadding(48,48,48,48);
            root.addView(missing, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return;
        }

        mapView = new MapView(this);
        Bundle mapState = savedInstanceState == null ? null : savedInstanceState.getBundle(MAP_STATE);
        mapView.onCreate(mapState);
        root.addView(mapView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("WFAG · GPS рута" + ((!from.isEmpty() || !to.isEmpty()) ? "\n" + from + " – " + to : ""));
        title.setTextSize(15f);
        title.setTextColor(Color.rgb(21,46,53));
        title.setBackgroundColor(Color.argb(235,255,255,255));
        title.setPadding(28,18,28,18);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setMargins(24,24,24,24);
        root.addView(title, lp);

        mapView.getMapAsync(this);
    }

    private boolean hasMapsKey() {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            String key = ai.metaData == null ? "" : ai.metaData.getString("com.google.android.geo.API_KEY", "");
            return key != null && !key.trim().isEmpty() && !key.contains("MAPS_API_KEY");
        } catch (Exception e) {
            return false;
        }
    }

    @Override public void onMapReady(GoogleMap map) {
        map.getUiSettings().setZoomControlsEnabled(true);
        map.getUiSettings().setCompassEnabled(true);
        map.getUiSettings().setMapToolbarEnabled(true);

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        int pointCount = 0;
        LatLng lastPoint = null;

        try {
            JSONObject state = new JSONObject(RouteTrackingService.stateJson(this));
            JSONArray sessions = state.optJSONArray("sessions");
            if (sessions == null) sessions = new JSONArray();

            for (int i = 0; i < sessions.length(); i++) {
                JSONObject session = sessions.optJSONObject(i);
                if (session == null) continue;
                String date = dateOf(session.optLong("startedAt"));
                if (!from.isEmpty() && date.compareTo(from) < 0) continue;
                if (!to.isEmpty() && date.compareTo(to) > 0) continue;

                JSONArray points = session.optJSONArray("points");
                PolylineOptions line = new PolylineOptions().width(8f).geodesic(true);
                int sessionPoints = 0;
                if (points != null) {
                    for (int j = 0; j < points.length(); j++) {
                        JSONObject p = points.optJSONObject(j);
                        if (p == null) continue;
                        LatLng q = new LatLng(p.optDouble("lat"), p.optDouble("lon"));
                        line.add(q);
                        bounds.include(q);
                        lastPoint = q;
                        pointCount++;
                        sessionPoints++;
                    }
                }
                if (sessionPoints >= 2) map.addPolyline(line);

                JSONArray stops = session.optJSONArray("stops");
                if (stops != null) {
                    for (int j = 0; j < stops.length(); j++) {
                        JSONObject stop = stops.optJSONObject(j);
                        if (stop == null) continue;
                        LatLng q = new LatLng(stop.optDouble("lat"), stop.optDouble("lon"));
                        bounds.include(q);
                        lastPoint = q;
                        pointCount++;
                        String name = stop.optString("name", "").trim();
                        if (name.isEmpty()) name = "Локација";
                        long start = stop.optLong("startAt");
                        long end = stop.optLong("endAt");
                        if (end <= 0) end = System.currentTimeMillis();
                        String snippet = clock(start) + " – " + clock(end) + " · задржување " + duration(start, end);
                        map.addMarker(new MarkerOptions().position(q).title(name).snippet(snippet));
                    }
                }
            }
        } catch (Exception ignored) {}

        if (pointCount == 0) {
            TextView empty = new TextView(this);
            empty.setText("Нема снимена GPS рута за избраниот период.");
            empty.setTextSize(16f);
            empty.setTextColor(Color.rgb(21,46,53));
            empty.setBackgroundColor(Color.argb(235,255,255,255));
            empty.setPadding(24,16,24,16);
            FrameLayout root = (FrameLayout) mapView.getParent();
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.setMargins(24,24,24,48);
            root.addView(empty, lp);
            return;
        }

        if (pointCount == 1 && lastPoint != null) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(lastPoint, 16f));
        } else {
            final LatLngBounds built = bounds.build();
            mapView.post(() -> {
                try { map.animateCamera(CameraUpdateFactory.newLatLngBounds(built, 90)); }
                catch (Exception ignored) {}
            });
        }
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

    @Override protected void onStart() { super.onStart(); if (mapView != null) mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override protected void onPause() { if (mapView != null) mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { if (mapView != null) mapView.onStop(); super.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }
    @Override protected void onDestroy() { if (mapView != null) mapView.onDestroy(); super.onDestroy(); }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            Bundle mapState = new Bundle();
            mapView.onSaveInstanceState(mapState);
            outState.putBundle(MAP_STATE, mapState);
        }
    }
}
