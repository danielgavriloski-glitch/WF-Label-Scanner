package mk.workfashion.personal;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Tag;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.datastore.MapReadResult;
import org.mapsforge.map.datastore.Way;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.util.LayerUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class RoadSnapper {
    private static final byte QUERY_ZOOM = 16;
    private static final double SEARCH_METERS = 140.0;
    private static final double MAX_SNAP_METERS = 70.0;

    private RoadSnapper() {}

    static SnapResult snap(File mapFile, List<LatLong> source) {
        if (source == null || source.size() < 2 || mapFile == null || !mapFile.exists()) {
            return new SnapResult(source == null ? new ArrayList<>() : new ArrayList<>(source), distance(source));
        }

        MapFile map = null;
        try {
            map = new MapFile(mapFile);
            int tileSize = map.getMapFileInfo().tilePixelSize;
            Map<String, List<Way>> cache = new HashMap<>();
            List<LatLong> out = new ArrayList<>(source.size());

            for (LatLong p : source) {
                if (p == null) continue;
                LatLong snapped = snapPoint(map, tileSize, p, cache);
                if (out.isEmpty() || meters(out.get(out.size() - 1), snapped) >= 1.5) {
                    out.add(snapped);
                }
            }

            if (out.size() < 2) out = new ArrayList<>(source);
            return new SnapResult(out, distance(out));
        } catch (Exception ignored) {
            return new SnapResult(new ArrayList<>(source), distance(source));
        } finally {
            if (map != null) {
                try { map.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static LatLong snapPoint(MapFile map, int tileSize, LatLong p, Map<String, List<Way>> cache) {
        String key = String.format(Locale.US, "%.3f:%.3f", p.latitude, p.longitude);
        List<Way> roads = cache.get(key);
        if (roads == null) {
            roads = readRoads(map, tileSize, p);
            cache.put(key, roads);
        }

        LatLong best = p;
        double bestMeters = MAX_SNAP_METERS;
        for (Way way : roads) {
            if (way == null || way.latLongs == null) continue;
            for (LatLong[] block : way.latLongs) {
                if (block == null || block.length < 2) continue;
                for (int i = 1; i < block.length; i++) {
                    Projection q = projectToSegment(p, block[i - 1], block[i]);
                    if (q != null && q.distanceMeters < bestMeters) {
                        bestMeters = q.distanceMeters;
                        best = q.point;
                    }
                }
            }
        }
        return best;
    }

    private static List<Way> readRoads(MapFile map, int tileSize, LatLong p) {
        List<Way> roads = new ArrayList<>();
        try {
            double latPad = SEARCH_METERS / 111320.0;
            double cos = Math.max(0.2, Math.cos(Math.toRadians(p.latitude)));
            double lonPad = SEARCH_METERS / (111320.0 * cos);
            BoundingBox box = new BoundingBox(
                    p.latitude - latPad, p.longitude - lonPad,
                    p.latitude + latPad, p.longitude + lonPad);

            Tile ul = LayerUtil.getUpperLeft(box, QUERY_ZOOM, tileSize);
            Tile lr = LayerUtil.getLowerRight(box, QUERY_ZOOM, tileSize);
            MapReadResult result = map.readMapData(ul, lr);
            if (result == null || result.ways == null) return roads;

            for (Way way : result.ways) {
                if (isDriveRoad(way)) roads.add(way);
            }
        } catch (Exception ignored) {}
        return roads;
    }

    private static boolean isDriveRoad(Way way) {
        if (way == null || way.tags == null) return false;
        String highway = null;
        for (Tag tag : way.tags) {
            if (tag != null && "highway".equals(tag.key)) {
                highway = tag.value;
                break;
            }
        }
        if (highway == null || highway.isEmpty()) return false;
        switch (highway) {
            case "footway":
            case "path":
            case "pedestrian":
            case "cycleway":
            case "steps":
            case "bridleway":
            case "corridor":
            case "platform":
                return false;
            default:
                return true;
        }
    }

    private static Projection projectToSegment(LatLong p, LatLong a, LatLong b) {
        if (p == null || a == null || b == null) return null;
        double cos = Math.max(0.2, Math.cos(Math.toRadians(p.latitude)));
        double ax = (a.longitude - p.longitude) * 111320.0 * cos;
        double ay = (a.latitude - p.latitude) * 111320.0;
        double bx = (b.longitude - p.longitude) * 111320.0 * cos;
        double by = (b.latitude - p.latitude) * 111320.0;
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        if (len2 < 0.01) return null;
        double t = -(ax * dx + ay * dy) / len2;
        t = Math.max(0.0, Math.min(1.0, t));
        double x = ax + t * dx, y = ay + t * dy;
        double d = Math.sqrt(x * x + y * y);
        double lat = p.latitude + y / 111320.0;
        double lon = p.longitude + x / (111320.0 * cos);
        return new Projection(new LatLong(lat, lon), d);
    }

    private static double distance(List<LatLong> points) {
        if (points == null || points.size() < 2) return 0.0;
        double total = 0.0;
        for (int i = 1; i < points.size(); i++) total += meters(points.get(i - 1), points.get(i));
        return total;
    }

    private static double meters(LatLong a, LatLong b) {
        if (a == null || b == null) return 0.0;
        double lat1 = Math.toRadians(a.latitude), lat2 = Math.toRadians(b.latitude);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude - a.longitude);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371000.0 * 2.0 * Math.atan2(Math.sqrt(h), Math.sqrt(Math.max(0.0, 1.0 - h)));
    }

    static final class SnapResult {
        final List<LatLong> points;
        final double distanceM;
        SnapResult(List<LatLong> points, double distanceM) {
            this.points = points;
            this.distanceM = distanceM;
        }
    }

    private static final class Projection {
        final LatLong point;
        final double distanceMeters;
        Projection(LatLong point, double distanceMeters) {
            this.point = point;
            this.distanceMeters = distanceMeters;
        }
    }
}
