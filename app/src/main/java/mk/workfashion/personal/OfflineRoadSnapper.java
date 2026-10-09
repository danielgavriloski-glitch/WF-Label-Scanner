package mk.workfashion.personal;

import android.content.Context;
import android.location.Location;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Tag;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.datastore.MapReadResult;
import org.mapsforge.map.datastore.Way;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.util.LayerUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Offline road map-matching for WFAG.
 *
 * Uses only the already downloaded Mapsforge Macedonia map. No GPS trace is
 * uploaded to a server. The route is constrained to OSM highway geometry and
 * A* is used between frequent GPS waypoints, so the visible blue line follows
 * the road instead of drawing straight chords between sparse GPS samples.
 */
final class OfflineRoadSnapper {
    private static final byte GRAPH_ZOOM = 15;
    private static final int TILE_SIZE = 256;
    private static final double TILE_MARGIN_DEG = 0.050; // ~5 km corridor so bends/interchanges stay connected
    private static final float TILE_SAMPLE_M = 800f;
    private static final float ROUTE_WAYPOINT_M = 800f;
    private static final float MAX_SNAP_M = 500f;
    private static final int MAX_TILES = 7000;
    private static final int MAX_ASTAR_VISITS = 90000;
    private static final int MAX_SNAP_CANDIDATES = 16;
    private static final int CACHE_VERSION = 4;
    private static final double GRID_DEG = 0.0025; // ~200-275 m in Macedonia

    static final class Result {
        final List<List<LatLong>> segments;
        final boolean fromCache;
        Result(List<List<LatLong>> segments, boolean fromCache) {
            this.segments = segments;
            this.fromCache = fromCache;
        }
    }

    private static final class TracePoint {
        final double lat, lon;
        final long at;
        TracePoint(double lat, double lon, long at) {
            this.lat = lat; this.lon = lon; this.at = at;
        }
        LatLong ll() { return new LatLong(lat, lon); }
    }

    private static final class Node {
        final String key;
        final LatLong p;
        final List<Edge> edges = new ArrayList<>();
        Node(String key, LatLong p) { this.key = key; this.p = p; }
    }

    private static final class Edge {
        final Node to;
        final double meters;
        Edge(Node to, double meters) { this.to = to; this.meters = meters; }
    }

    private static final class QueueState implements Comparable<QueueState> {
        final Node node;
        final double f;
        QueueState(Node node, double f) { this.node = node; this.f = f; }
        @Override public int compareTo(QueueState other) { return Double.compare(f, other.f); }
    }

    private static final class Graph {
        final Map<String, Node> nodes = new HashMap<>();
        final Set<String> edgeKeys = new HashSet<>();
        final Map<String, List<Node>> grid = new HashMap<>();

        Node node(LatLong p) {
            String key = nodeKey(p.latitude, p.longitude);
            Node n = nodes.get(key);
            if (n == null) {
                n = new Node(key, p);
                nodes.put(key, n);
                String cell = cellKey(p.latitude, p.longitude);
                List<Node> list = grid.get(cell);
                if (list == null) { list = new ArrayList<>(); grid.put(cell, list); }
                list.add(n);
            }
            return n;
        }

        void addEdge(LatLong a, LatLong b) {
            double d = dist(a.latitude, a.longitude, b.latitude, b.longitude);
            if (d < 0.5 || d > 3000) return;
            Node na = node(a), nb = node(b);
            String ek = na.key.compareTo(nb.key) < 0 ? na.key + "|" + nb.key : nb.key + "|" + na.key;
            if (!edgeKeys.add(ek)) return;
            na.edges.add(new Edge(nb, d));
            nb.edges.add(new Edge(na, d));
        }

        Node nearest(TracePoint p) {
            List<Node> candidates = nearestCandidates(p, 1);
            return candidates.isEmpty() ? null : candidates.get(0);
        }

        List<Node> nearestCandidates(TracePoint p, int limit) {
            int cy = cell(p.lat), cx = cell(p.lon);
            List<Node> found = new ArrayList<>();
            int radius = 3;
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    List<Node> list = grid.get((cy + dy) + ":" + (cx + dx));
                    if (list == null) continue;
                    for (Node n : list) {
                        double d = dist(p.lat, p.lon, n.p.latitude, n.p.longitude);
                        if (d <= MAX_SNAP_M) found.add(n);
                    }
                }
            }
            Collections.sort(found, (a, b) -> Double.compare(
                    dist(p.lat, p.lon, a.p.latitude, a.p.longitude),
                    dist(p.lat, p.lon, b.p.latitude, b.p.longitude)));
            if (found.size() > limit) return new ArrayList<>(found.subList(0, limit));
            return found;
        }
    }

    private OfflineRoadSnapper() {}

    static Result snap(Context context, MapFile mapFile, JSONObject session) {
        try {
            List<TracePoint> trace = trace(session.optJSONArray("points"));
            if (trace.size() < 2) return new Result(Collections.emptyList(), false);

            String signature = signature(session, trace);
            List<List<LatLong>> cached = readCache(context, session.optString("id", "route"), signature);
            if (cached != null && !cached.isEmpty()) return new Result(cached, true);

            Set<Tile> tiles = collectCorridorTiles(trace);
            if (tiles.isEmpty() || tiles.size() > MAX_TILES) return new Result(Collections.emptyList(), false);

            Graph graph = buildGraph(mapFile, tiles);
            if (graph.nodes.size() < 2) return new Result(Collections.emptyList(), false);

            List<TracePoint> waypoints = routeWaypoints(trace);
            List<List<LatLong>> segments = route(graph, waypoints);

            // Never replace the visible GPS line with a tiny partial match.
            // A road-snapped result is accepted only when it reaches both ends
            // of the recorded trip and contains a meaningful amount of road.
            if (!isFullTripMatch(trace, segments)) {
                return new Result(Collections.emptyList(), false);
            }

            writeCache(context, session.optString("id", "route"), signature, segments);
            return new Result(segments, false);
        } catch (Exception ignored) {
            return new Result(Collections.emptyList(), false);
        }
    }

    private static List<TracePoint> trace(JSONArray points) {
        List<TracePoint> out = new ArrayList<>();
        if (points == null) return out;
        TracePoint last = null;
        for (int i = 0; i < points.length(); i++) {
            JSONObject p = points.optJSONObject(i);
            if (p == null) continue;
            double lat = p.optDouble("lat", Double.NaN);
            double lon = p.optDouble("lon", Double.NaN);
            if (Double.isNaN(lat) || Double.isNaN(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) continue;
            double accuracy = p.optDouble("accuracy", 35d);
            if (accuracy > 180d) continue;
            TracePoint cur = new TracePoint(lat, lon, p.optLong("at", 0L));
            if (last != null) {
                double d = dist(last.lat, last.lon, cur.lat, cur.lon);
                long dt = Math.max(1000L, cur.at - last.at);
                double speed = d / (dt / 1000d);
                if (d > 450d && speed > 75d) continue; // obvious GPS teleport
                if (d < 2d && dt < 10000L) continue;
            }
            out.add(cur);
            last = cur;
        }
        return out;
    }

    private static Set<Tile> collectCorridorTiles(List<TracePoint> trace) {
        Set<Tile> tiles = new HashSet<>();
        TracePoint a = trace.get(0);
        for (int i = 1; i < trace.size(); i++) {
            TracePoint b = trace.get(i);
            double d = dist(a.lat, a.lon, b.lat, b.lon);
            int steps = Math.max(1, (int)Math.ceil(d / TILE_SAMPLE_M));
            for (int s = 0; s <= steps; s++) {
                double t = s / (double)steps;
                double lat = a.lat + (b.lat - a.lat) * t;
                double lon = a.lon + (b.lon - a.lon) * t;
                BoundingBox bb = new BoundingBox(
                        lat - TILE_MARGIN_DEG, lon - TILE_MARGIN_DEG,
                        lat + TILE_MARGIN_DEG, lon + TILE_MARGIN_DEG);
                tiles.addAll(LayerUtil.getTiles(bb, GRAPH_ZOOM, TILE_SIZE));
                if (tiles.size() > MAX_TILES) return tiles;
            }
            a = b;
        }
        return tiles;
    }

    private static Graph buildGraph(MapFile mapFile, Set<Tile> tiles) {
        Graph g = new Graph();
        for (Tile tile : tiles) {
            MapReadResult rr;
            try { rr = mapFile.readMapData(tile); } catch (Exception e) { continue; }
            if (rr == null || rr.ways == null) continue;
            for (Way way : rr.ways) {
                if (way == null || !isDriveRoad(way.tags) || way.latLongs == null) continue;
                for (LatLong[] part : way.latLongs) {
                    if (part == null || part.length < 2) continue;
                    for (int i = 1; i < part.length; i++) {
                        LatLong a = part[i - 1], b = part[i];
                        if (a != null && b != null) g.addEdge(a, b);
                    }
                }
            }
        }
        return g;
    }

    private static boolean isDriveRoad(List<Tag> tags) {
        if (tags == null) return false;
        String highway = null;
        String access = null;
        for (Tag t : tags) {
            if (t == null) continue;
            if ("highway".equals(t.key)) highway = t.value;
            else if ("access".equals(t.key)) access = t.value;
        }
        if (highway == null || highway.isEmpty()) return false;
        if ("no".equals(access) || "private".equals(access)) return false;
        switch (highway) {
            case "motorway": case "motorway_link":
            case "trunk": case "trunk_link":
            case "primary": case "primary_link":
            case "secondary": case "secondary_link":
            case "tertiary": case "tertiary_link":
            case "unclassified": case "residential":
            case "service": case "living_street":
            case "road":
                return true;
            default:
                return false;
        }
    }

    private static List<TracePoint> routeWaypoints(List<TracePoint> trace) {
        List<TracePoint> out = new ArrayList<>();
        TracePoint anchor = trace.get(0);
        out.add(anchor);
        for (int i = 1; i < trace.size() - 1; i++) {
            TracePoint p = trace.get(i);
            double d = dist(anchor.lat, anchor.lon, p.lat, p.lon);
            long dt = p.at > 0 && anchor.at > 0 ? p.at - anchor.at : 0L;
            if (d >= ROUTE_WAYPOINT_M || dt >= 120000L) {
                out.add(p);
                anchor = p;
            }
        }
        TracePoint last = trace.get(trace.size() - 1);
        if (out.get(out.size() - 1) != last) out.add(last);
        return out;
    }

    private static List<List<LatLong>> route(Graph graph, List<TracePoint> waypoints) {
        List<List<LatLong>> result = new ArrayList<>();
        List<LatLong> current = new ArrayList<>();
        Node previous = null;

        for (TracePoint tp : waypoints) {
            List<Node> candidates = graph.nearestCandidates(tp, MAX_SNAP_CANDIDATES);
            if (candidates.isEmpty()) {
                // Do not cut the route. Keep the previous road anchor and bridge
                // from it to the next GPS waypoint that can be snapped.
                continue;
            }

            if (previous == null) {
                previous = candidates.get(0);
                current.add(previous.p);
                continue;
            }

            List<Node> bestPath = null;
            Node chosen = null;
            double bestScore = Double.POSITIVE_INFINITY;

            for (Node candidate : candidates) {
                if (candidate == previous) {
                    chosen = candidate;
                    bestPath = Collections.singletonList(candidate);
                    bestScore = 0d;
                    break;
                }

                List<Node> path = aStar(previous, candidate);
                if (path == null || path.size() < 2) continue;

                double pathMeters = pathLength(path);
                double snapMeters = dist(tp.lat, tp.lon, candidate.p.latitude, candidate.p.longitude);
                double direct = dist(previous.p.latitude, previous.p.longitude, candidate.p.latitude, candidate.p.longitude);
                double detour = Math.max(0d, pathMeters - direct);
                double score = snapMeters * 2.2d + detour * 0.08d;

                if (score < bestScore) {
                    bestScore = score;
                    bestPath = path;
                    chosen = candidate;
                }
            }

            if (chosen == null || bestPath == null) {
                // A GPS point may sit closer to the opposite carriageway or to
                // an isolated service road. Ignore that single bad match rather
                // than starting a new blue segment.
                continue;
            }

            for (int i = 1; i < bestPath.size(); i++) {
                LatLong p = bestPath.get(i).p;
                if (current.isEmpty() || dist(
                        current.get(current.size()-1).latitude,
                        current.get(current.size()-1).longitude,
                        p.latitude, p.longitude) > 0.5) {
                    current.add(p);
                }
            }
            previous = chosen;
        }

        if (current.size() >= 2) result.add(current);
        return result;
    }

    private static double pathLength(List<Node> path) {
        double total = 0d;
        for (int i = 1; i < path.size(); i++) {
            LatLong a = path.get(i - 1).p;
            LatLong b = path.get(i).p;
            total += dist(a.latitude, a.longitude, b.latitude, b.longitude);
        }
        return total;
    }

    private static List<Node> aStar(Node start, Node goal) {
        PriorityQueue<QueueState> open = new PriorityQueue<>();
        Map<Node, Double> gScore = new HashMap<>();
        Map<Node, Node> came = new HashMap<>();
        Set<Node> closed = new HashSet<>();

        double direct = dist(start.p.latitude, start.p.longitude, goal.p.latitude, goal.p.longitude);
        double maxRoute = Math.max(7000d, direct * 10.0d + 5000d);

        gScore.put(start, 0d);
        open.add(new QueueState(start, direct));
        int visits = 0;

        while (!open.isEmpty() && visits++ < MAX_ASTAR_VISITS) {
            Node cur = open.poll().node;
            if (!closed.add(cur)) continue;
            if (cur == goal) {
                List<Node> path = new ArrayList<>();
                Node n = goal;
                path.add(n);
                while (came.containsKey(n)) {
                    n = came.get(n);
                    path.add(n);
                }
                Collections.reverse(path);
                return path;
            }

            double base = gScore.containsKey(cur) ? gScore.get(cur) : Double.POSITIVE_INFINITY;
            if (base > maxRoute) continue;

            for (Edge e : cur.edges) {
                if (closed.contains(e.to)) continue;
                double tentative = base + e.meters;
                if (tentative > maxRoute) continue;
                Double old = gScore.get(e.to);
                if (old == null || tentative < old) {
                    came.put(e.to, cur);
                    gScore.put(e.to, tentative);
                    double h = dist(e.to.p.latitude, e.to.p.longitude, goal.p.latitude, goal.p.longitude);
                    open.add(new QueueState(e.to, tentative + h));
                }
            }
        }
        return null;
    }

    private static boolean isFullTripMatch(List<TracePoint> trace, List<List<LatLong>> segments) {
        if (trace == null || trace.size() < 2 || segments == null || segments.isEmpty()) return false;

        List<LatLong> road = new ArrayList<>();
        double snappedMeters = 0d;
        for (List<LatLong> line : segments) {
            if (line == null || line.size() < 2) continue;
            for (int i = 0; i < line.size(); i++) {
                LatLong p = line.get(i);
                if (p == null) continue;
                if (road.isEmpty() || dist(
                        road.get(road.size()-1).latitude,
                        road.get(road.size()-1).longitude,
                        p.latitude, p.longitude) > 0.5d) {
                    road.add(p);
                }
                if (i > 0) {
                    LatLong a = line.get(i - 1);
                    snappedMeters += dist(a.latitude, a.longitude, p.latitude, p.longitude);
                }
            }
        }
        if (road.size() < 8 || snappedMeters < 500d) return false;

        double rawMeters = 0d;
        for (int i = 1; i < trace.size(); i++) {
            TracePoint a = trace.get(i - 1), b = trace.get(i);
            rawMeters += dist(a.lat, a.lon, b.lat, b.lon);
        }
        if (rawMeters < 500d) return false;

        TracePoint firstTrace = trace.get(0);
        TracePoint lastTrace = trace.get(trace.size() - 1);
        LatLong firstRoad = road.get(0);
        LatLong lastRoad = road.get(road.size() - 1);

        double startGap = dist(firstTrace.lat, firstTrace.lon, firstRoad.latitude, firstRoad.longitude);
        double endGap = dist(lastTrace.lat, lastTrace.lon, lastRoad.latitude, lastRoad.longitude);
        if (startGap > MAX_SNAP_M * 1.6d || endGap > MAX_SNAP_M * 1.6d) return false;

        // The matched road must represent the whole recorded trip, not a tiny
        // successful fragment. This is the main protection against the
        // "full line for one second, then only one dot" failure.
        if (snappedMeters < rawMeters * 0.65d) return false;
        if (snappedMeters > rawMeters * 3.0d + 3000d) return false;

        // Check coverage across the entire trip, not only the start and end.
        int samples = Math.min(20, trace.size());
        int covered = 0;
        for (int s = 0; s < samples; s++) {
            int idx = samples == 1 ? 0 : (int)Math.round((trace.size() - 1) * (s / (double)(samples - 1)));
            TracePoint tp = trace.get(idx);
            double best = Double.POSITIVE_INFINITY;
            for (LatLong rp : road) {
                double d = dist(tp.lat, tp.lon, rp.latitude, rp.longitude);
                if (d < best) best = d;
                if (best <= 700d) break;
            }
            if (best <= 700d) covered++;
        }
        return covered >= Math.max(3, (int)Math.ceil(samples * 0.75d));
    }

    private static String signature(JSONObject session, List<TracePoint> trace) {
        TracePoint last = trace.get(trace.size() - 1);
        return CACHE_VERSION + "-" + trace.size() + "-" + last.at + "-" + session.optLong("endedAt", 0L);
    }

    private static File cacheFile(Context context, String sessionId) {
        File dir = new File(context.getFilesDir(), "road-snap-cache");
        if (!dir.exists()) dir.mkdirs();
        String safe = sessionId == null ? "route" : sessionId.replaceAll("[^A-Za-z0-9_.-]", "_");
        return new File(dir, safe + ".json");
    }

    private static List<List<LatLong>> readCache(Context context, String sessionId, String signature) {
        File f = cacheFile(context, sessionId);
        if (!f.exists() || f.length() <= 0 || f.length() > 8 * 1024 * 1024) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int off = 0, n;
            while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
            JSONObject root = new JSONObject(new String(data, 0, off, StandardCharsets.UTF_8));
            if (!signature.equals(root.optString("signature"))) return null;
            JSONArray segs = root.optJSONArray("segments");
            if (segs == null) return null;
            List<List<LatLong>> out = new ArrayList<>();
            for (int i = 0; i < segs.length(); i++) {
                JSONArray arr = segs.optJSONArray(i);
                if (arr == null) continue;
                List<LatLong> line = new ArrayList<>();
                for (int j = 0; j < arr.length(); j++) {
                    JSONArray p = arr.optJSONArray(j);
                    if (p != null && p.length() >= 2) line.add(new LatLong(p.optDouble(0), p.optDouble(1)));
                }
                if (line.size() >= 2) out.add(line);
            }
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void writeCache(Context context, String sessionId, String signature, List<List<LatLong>> segments) {
        try {
            JSONObject root = new JSONObject();
            root.put("signature", signature);
            JSONArray segs = new JSONArray();
            for (List<LatLong> line : segments) {
                JSONArray arr = new JSONArray();
                for (LatLong p : line) {
                    JSONArray pair = new JSONArray();
                    pair.put(p.latitude);
                    pair.put(p.longitude);
                    arr.put(pair);
                }
                segs.put(arr);
            }
            root.put("segments", segs);
            byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 8 * 1024 * 1024) return;
            try (FileOutputStream out = new FileOutputStream(cacheFile(context, sessionId), false)) {
                out.write(bytes);
                out.flush();
            }
        } catch (Exception ignored) {}
    }

    private static int cell(double value) { return (int)Math.floor(value / GRID_DEG); }
    private static String cellKey(double lat, double lon) { return cell(lat) + ":" + cell(lon); }

    private static String nodeKey(double lat, double lon) {
        long a = Math.round(lat * 1_000_000d);
        long b = Math.round(lon * 1_000_000d);
        return String.format(Locale.US, "%d:%d", a, b);
    }

    private static double dist(double aLat, double aLon, double bLat, double bLon) {
        float[] r = new float[1];
        Location.distanceBetween(aLat, aLon, bLat, bLon, r);
        return r[0];
    }
}
