package gov.gujarat.rnb.infratrack.api;

import gov.gujarat.rnb.infratrack.core.*;
import gov.gujarat.rnb.infratrack.db.Db;
import gov.gujarat.rnb.infratrack.db.Where;
import gov.gujarat.rnb.infratrack.http.ApiException;
import gov.gujarat.rnb.infratrack.http.Router;

import java.time.LocalDateTime;
import java.util.*;

/** Citizen complaints (auto-routed), public portal data, and the maintenance priority ranking. */
public final class CitizenApi {
    private CitizenApi() {}

    private static final List<String> CATEGORIES = List.of("Pothole on road", "Road cave-in", "Cracks in bridge", "Damaged bridge railing",
            "Waterlogging on road", "Blocked culvert", "Damaged government building", "Missing road signage", "Broken divider");
    private static final Set<String> HIGH = Set.of("Cracks in bridge", "Damaged bridge railing", "Road cave-in");
    private static final Set<String> LOW = Set.of("Missing road signage", "Broken divider");
    private static final Set<String> STATUSES = Set.of("open", "assigned", "in_progress", "resolved");

    public static void register(Router r) {
        r.get("/api/complaints/categories", req -> CATEGORIES);

        r.post("/api/complaints", req -> {
            User u = Auth.optionalUser(req);
            String category = req.str("category", "");
            if (!CATEGORIES.contains(category)) throw ApiException.bad("Choose a problem type from the list");
            String description = req.requiredStr("description", 5, "Description");
            String location = req.requiredStr("location_text", 1, "Landmark or address");
            String name = req.requiredStr("citizen_name", 2, "Your name");
            double lat = req.num("lat", "Latitude", 20, 24.8), lng = req.num("lng", "Longitude", 68, 74.6);

            Hierarchy.SubDivision nearest = null;
            double best = Double.MAX_VALUE;
            for (var s : Hierarchy.subdivisions()) {
                double d = distanceKm(lat, lng, s.lat(), s.lng());
                if (d < best) { best = d; nearest = s; }
            }
            LocalDateTime now = LocalDateTime.now().withNano(0);
            long next = Db.count("SELECT COALESCE(MAX(id), 0) FROM complaints") + 1;
            String ticket = String.format("GRB-%d-%06d", now.getYear(), next);
            String priority = HIGH.contains(category) ? "high" : LOW.contains(category) ? "low" : "medium";
            long id = Db.insert("INSERT INTO complaints (ticket, category, description, location_text, lat, lng, region_id, division_id, subdivision_id, "
                            + "citizen_name, phone, user_id, priority, status, resolution_note, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    ticket, category, description, location, lat, lng, nearest.regionId(), nearest.divisionId(), nearest.id(), name,
                    req.str("phone", ""), u == null ? null : u.id(), priority, "open", "", now, now);
            Stats.audit(name, "Complaint filed", "complaint", ticket, category + " — " + location, nearest.regionId());
            Map<String, Object> out = out(Db.one("SELECT * FROM complaints WHERE id = ?", id));
            out.put("routing", String.format("Auto-routed to %s Sub-Division (%.1f km away), %s", nearest.name(), best,
                    Hierarchy.division(nearest.divisionId()).name()));
            return out;
        });

        r.get("/api/complaints/track/{ticket}", req -> {
            var c = Db.one("SELECT * FROM complaints WHERE ticket = ?", req.param("ticket").trim().toUpperCase(Locale.ROOT));
            if (c == null) throw ApiException.notFound("No complaint with that ticket number was");
            Map<String, Object> o = out(c);
            o.remove("citizen_name");
            return o;
        });

        r.get("/api/complaints", req -> {
            User u = Auth.user(req);
            Where w;
            if (u.is("citizen")) w = new Where().and("user_id = ?", u.id());
            else if (u.isOfficer()) w = Scope.complaints(u);
            else throw ApiException.forbidden("Contractors do not handle citizen complaints");
            String status = req.q("status"), priority = req.q("priority");
            Map<String, Object> counts = new LinkedHashMap<>();
            if (status == null)
                for (var x : Db.query("SELECT status, COUNT(*) AS n FROM complaints WHERE " + w.sql() + " GROUP BY status", w.args()))
                    counts.put(Db.s(x, "status"), Db.l(x, "n"));
            w.andIf(status != null, "status = ?", status);
            w.andIf(priority != null, "priority = ?", priority);
            int page = req.qInt("page", 1, 1, 100000), size = req.qInt("page_size", 25, 1, 100);
            long total = Db.count("SELECT COUNT(id) FROM complaints WHERE " + w.sql(), w.args());
            List<Map<String, Object>> items = new ArrayList<>();
            for (var c : Db.query("SELECT * FROM complaints WHERE " + w.sql() + " ORDER BY CASE WHEN status = 'resolved' THEN 1 ELSE 0 END, "
                    + "CASE priority WHEN 'high' THEN 0 WHEN 'medium' THEN 1 ELSE 2 END, created_at DESC LIMIT ? OFFSET ?", w.args(size, (page - 1) * size)))
                items.add(out(c));
            return M.of("total", total, "counts", counts, "items", items);
        });

        r.patch("/api/complaints/{id}", req -> {
            User u = Auth.officer(req);
            String status = req.str("status", "");
            if (!STATUSES.contains(status)) throw ApiException.bad("Unknown status");
            int id = req.paramInt("id");
            var c = Db.one("SELECT * FROM complaints WHERE id = ?", id);
            if (c == null) throw ApiException.notFound("Complaint");
            if (!Scope.canAccess(u, Db.i(c, "region_id"), Db.i(c, "division_id"), Db.i(c, "subdivision_id")))
                throw ApiException.forbidden("This complaint is outside your jurisdiction");
            Db.update("UPDATE complaints SET status = ?, resolution_note = ?, updated_at = ? WHERE id = ?",
                    status, req.str("resolution_note", ""), LocalDateTime.now().withNano(0), id);
            Stats.audit(u.name(), "Complaint " + status.replace('_', ' '), "complaint", c.get("ticket"), Db.s(c, "category"), Db.i(c, "region_id"));
            return out(Db.one("SELECT * FROM complaints WHERE id = ?", id));
        });

        // ------------------------------------------------------------ public portal
        r.get("/api/public/stats", req -> {
            List<Map<String, Object>> regions = new ArrayList<>();
            long total = 0;
            for (var x : Db.query("SELECT r.id, r.name, r.is_focus, COUNT(a.id) AS n FROM regions r JOIN assets a ON a.region_id = r.id GROUP BY r.id, r.name, r.is_focus ORDER BY r.id")) {
                regions.add(M.of("id", x.get("id"), "name", x.get("name"), "is_focus", x.get("is_focus"), "assets", Db.l(x, "n")));
                total += Db.l(x, "n");
            }
            Map<String, Long> byType = new HashMap<>();
            for (var x : Db.query("SELECT asset_type, COUNT(*) AS n FROM assets GROUP BY asset_type")) byType.put(Db.s(x, "asset_type"), Db.l(x, "n"));
            Map<String, Long> proj = new HashMap<>();
            for (var x : Db.query("SELECT status, COUNT(*) AS n FROM projects GROUP BY status")) proj.put(Db.s(x, "status"), Db.l(x, "n"));
            long resolved = Db.count("SELECT COUNT(*) FROM complaints WHERE status = 'resolved'");
            long allC = Math.max(1, Db.count("SELECT COUNT(*) FROM complaints"));
            Object km = Db.scalar("SELECT SUM(length_km) FROM assets");
            return M.of("total_assets", total, "road_km", Math.round(km == null ? 0 : ((Number) km).doubleValue()),
                    "bridges", byType.getOrDefault("bridge", 0L),
                    "buildings", byType.getOrDefault("building", 0L) + byType.getOrDefault("facility", 0L),
                    "active_projects", proj.getOrDefault("in_progress", 0L), "completed_projects", proj.getOrDefault("completed", 0L),
                    "complaints_resolved_pct", Math.round(resolved * 100.0 / allC), "regions", regions);
        });

        r.get("/api/public/works", req -> {
            Integer region = req.qInt("region_id");
            Where w = new Where().and("status = 'in_progress'").andIf(region != null, "region_id = ?", region);
            List<Map<String, Object>> out = new ArrayList<>();
            for (var p : Db.query("SELECT * FROM projects WHERE " + w.sql() + " ORDER BY sanctioned_cost_cr DESC LIMIT 12", w.args()))
                out.add(M.of("code", p.get("code"), "name", p.get("name"), "region", Hierarchy.region(Db.i(p, "region_id")).name(),
                        "progress", p.get("physical_progress"), "cost_cr", p.get("sanctioned_cost_cr"), "target_date", p.get("target_date")));
            return out;
        });

        // ------------------------------------------------------------ predictive maintenance
        r.get("/api/analytics/priority", req -> {
            User u = Auth.officer(req);
            Where w = Scope.assets(u);
            w.andIf(req.q("asset_type") != null, "asset_type = ?", req.q("asset_type"));
            int limit = req.qInt("limit", 40, 1, 200);
            List<Map<String, Object>> items = new ArrayList<>();
            double totalCost = 0;
            for (var a : Db.query("SELECT * FROM assets WHERE " + w.sql() + " ORDER BY risk_score DESC, id LIMIT ?", w.args(limit))) {
                var f = Health.forecast(Db.s(a, "asset_type"), Db.d(a, "condition_score"), Db.i(a, "year_built"),
                        Db.i(a, "traffic_aadt"), Db.d(a, "replacement_value_cr"));
                double cost = (double) f.get("estimated_cost_lakh");
                totalCost += cost;
                items.add(M.of("id", a.get("id"), "code", a.get("asset_code"), "name", a.get("name"), "type", a.get("asset_type"),
                        "score", a.get("condition_score"), "condition", a.get("condition"), "risk", a.get("risk_score"),
                        "year_built", a.get("year_built"), "aadt", a.get("traffic_aadt"),
                        "subdivision", Hierarchy.subdivision(Db.i(a, "subdivision_id")).name(),
                        "months_to_critical", f.get("months_to_critical"), "action", f.get("recommended_action"),
                        "cost_lakh", cost, "deterioration", f.get("deterioration_per_year")));
            }
            var bands = Db.one("SELECT SUM(CASE WHEN risk_score >= 60 THEN 1 ELSE 0 END) AS hi, "
                    + "SUM(CASE WHEN risk_score >= 40 AND risk_score < 60 THEN 1 ELSE 0 END) AS mid, "
                    + "SUM(CASE WHEN risk_score < 40 THEN 1 ELSE 0 END) AS lo FROM assets WHERE " + w.sql(), w.args());
            return M.of("items", items, "bands", M.of("high", Db.l(bands, "hi"), "elevated", Db.l(bands, "mid"), "low", Db.l(bands, "lo")),
                    "total_cost_lakh", M.round(totalCost, 1));
        });
    }

    private static double distanceKm(double aLat, double aLng, double bLat, double bLng) {
        double p = Math.PI / 180;
        double h = Math.pow(Math.sin((bLat - aLat) * p / 2), 2)
                + Math.cos(aLat * p) * Math.cos(bLat * p) * Math.pow(Math.sin((bLng - aLng) * p / 2), 2);
        return 12742 * Math.asin(Math.sqrt(h));
    }

    private static Map<String, Object> out(Map<String, Object> c) {
        var s = Hierarchy.subdivision(Db.i(c, "subdivision_id"));
        return M.of("id", c.get("id"), "ticket", c.get("ticket"), "category", c.get("category"), "description", c.get("description"),
                "location", c.get("location_text"), "lat", c.get("lat"), "lng", c.get("lng"), "priority", c.get("priority"),
                "status", c.get("status"), "citizen_name", c.get("citizen_name"), "resolution_note", c.get("resolution_note"),
                "subdivision", s.name(), "division", Hierarchy.division(s.divisionId()).name(), "region", Hierarchy.region(s.regionId()).name(),
                "created_at", c.get("created_at"), "updated_at", c.get("updated_at"));
    }
}
