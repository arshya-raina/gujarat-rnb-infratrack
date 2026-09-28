package gov.gujarat.rnb.infratrack.api;

import gov.gujarat.rnb.infratrack.core.*;
import gov.gujarat.rnb.infratrack.db.Db;
import gov.gujarat.rnb.infratrack.db.Where;
import gov.gujarat.rnb.infratrack.http.ApiException;
import gov.gujarat.rnb.infratrack.http.Req;
import gov.gujarat.rnb.infratrack.http.Router;

import java.time.LocalDate;
import java.util.*;

/** Infrastructure registry: list, map points, detail with forecast, and field inspections. */
public final class AssetApi {
    private AssetApi() {}

    private static final Map<String, String> SORTS = Map.of(
            "risk", "risk_score DESC", "condition", "condition_score ASC", "name", "name ASC",
            "due", "next_maintenance_date ASC", "age", "year_built ASC", "value", "replacement_value_cr DESC");

    public static void register(Router r) {
        r.get("/api/assets", req -> {
            User u = Auth.officer(req);
            Where w = filters(u, req, true);
            int page = req.qInt("page", 1, 1, 100000), size = req.qInt("page_size", 25, 1, 100);
            String order = SORTS.getOrDefault(Optional.ofNullable(req.q("sort")).orElse("risk"), SORTS.get("risk"));
            long total = Db.count("SELECT COUNT(id) FROM assets WHERE " + w.sql(), w.args());
            List<Map<String, Object>> items = new ArrayList<>();
            for (var a : Db.query("SELECT * FROM assets WHERE " + w.sql() + " ORDER BY " + order + ", id LIMIT ? OFFSET ?",
                    w.args(size, (page - 1) * size)))
                items.add(row(a));
            return M.of("total", total, "page", page, "page_size", size, "items", items);
        });

        r.get("/api/assets/geo", req -> {
            User u = Auth.officer(req);
            Where w = filters(u, req, false);
            int limit = req.qInt("limit", 4000, 1, 20000);
            List<Map<String, Object>> out = new ArrayList<>();
            for (var a : Db.query("SELECT id, name, asset_type, condition, lat, lng FROM assets WHERE " + w.sql() + " ORDER BY risk_score DESC LIMIT ?", w.args(limit)))
                out.add(M.of("id", a.get("id"), "name", a.get("name"), "type", a.get("asset_type"), "condition", a.get("condition"),
                        "lat", a.get("lat"), "lng", a.get("lng")));
            return out;
        });

        r.get("/api/assets/{id}", req -> detail(Auth.officer(req), req.paramInt("id")));

        r.post("/api/assets/{id}/inspections", req -> {
            User u = Auth.require(req, "subdivision_engineer", "division_engineer", "regional_officer", "state_admin");
            int id = req.paramInt("id");
            var a = Db.one("SELECT * FROM assets WHERE id = ?", id);
            if (a == null) throw ApiException.notFound("Asset");
            Scope.ensure(u, Db.i(a, "region_id"), Db.i(a, "division_id"), Db.i(a, "subdivision_id"));
            double s = req.num("condition_score", "Condition index", 0, 100);
            String defects = req.str("defects", ""), remarks = req.str("remarks", ""), status = req.str("status", "");
            if (!status.isEmpty() && !Set.of("operational", "restricted", "closed").contains(status)) throw ApiException.bad("Unknown status");
            LocalDate today = LocalDate.now();
            String action = s < 25 ? "urgent" : s < 50 ? "repair" : "routine";
            String designation = u.designation().split(",")[0];
            Db.update("INSERT INTO inspections (asset_id, date, inspector, condition_score, defects, action, remarks) VALUES (?,?,?,?,?,?,?)",
                    id, today, u.name() + " (" + designation + ")", s, defects, action, remarks);
            String band = Health.band(s);
            LocalDate next = today.plusDays(s < 25 ? 0 : s < 50 ? 30 : s < 75 ? 180 : 365);
            double risk = Health.risk(s, Db.i(a, "year_built"), Db.i(a, "traffic_aadt"), today, Db.s(a, "asset_type"), today);
            Db.update("UPDATE assets SET condition_score = ?, condition = ?, last_inspection_date = ?, next_maintenance_date = ?, risk_score = ?, "
                    + "status = COALESCE(?, status) WHERE id = ?", s, band, today, next, risk, status.isEmpty() ? null : status, id);
            Stats.audit(u.name(), "Inspection logged", "asset", a.get("asset_code"),
                    String.format("Condition %.0f/100 (%s) — %s", s, band, a.get("name")), Db.i(a, "region_id"));
            return detail(u, id);
        });
    }

    private static Where filters(User u, Req req, boolean withSearch) {
        Where w = Scope.assets(u);
        Integer region = req.qInt("region_id"), division = req.qInt("division_id"), sub = req.qInt("subdivision_id");
        w.andIf(region != null, "region_id = ?", region);
        w.andIf(division != null, "division_id = ?", division);
        w.andIf(sub != null, "subdivision_id = ?", sub);
        w.andIf(req.q("asset_type") != null, "asset_type = ?", req.q("asset_type"));
        w.andIf(req.q("condition") != null, "condition = ?", req.q("condition"));
        if (withSearch) {
            w.andIf(req.qBool("due"), "next_maintenance_date <= ?", LocalDate.now());
            String q = req.q("q");
            if (q != null) {
                String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                w.and("(LOWER(name) LIKE ? OR LOWER(asset_code) LIKE ? OR LOWER(category) LIKE ?)", like, like, like);
            }
        }
        return w;
    }

    static Map<String, Object> row(Map<String, Object> a) {
        return M.of("id", a.get("id"), "code", a.get("asset_code"), "name", a.get("name"), "type", a.get("asset_type"),
                "category", a.get("category"), "condition", a.get("condition"), "score", a.get("condition_score"),
                "risk", a.get("risk_score"), "status", a.get("status"), "next_maintenance", a.get("next_maintenance_date"),
                "last_inspection", a.get("last_inspection_date"), "year_built", a.get("year_built"), "region_id", a.get("region_id"),
                "division_id", a.get("division_id"), "subdivision_id", a.get("subdivision_id"), "lat", a.get("lat"), "lng", a.get("lng"),
                "value_cr", a.get("replacement_value_cr"));
    }

    private static Map<String, Object> detail(User u, int id) {
        var a = Db.one("SELECT * FROM assets WHERE id = ?", id);
        if (a == null) throw ApiException.notFound("Asset");
        Scope.ensure(u, Db.i(a, "region_id"), Db.i(a, "division_id"), Db.i(a, "subdivision_id"));
        Map<String, Object> out = row(a);
        var reg = Hierarchy.region(Db.i(a, "region_id"));
        out.put("length_km", a.get("length_km"));
        out.put("span_m", a.get("span_m"));
        out.put("floors", a.get("floors"));
        out.put("traffic_aadt", a.get("traffic_aadt"));
        out.put("subdivision", Hierarchy.subdivision(Db.i(a, "subdivision_id")).name());
        out.put("division", Hierarchy.division(Db.i(a, "division_id")).name());
        out.put("region", reg.name());
        out.put("circle", reg.circleName());
        out.put("forecast", Health.forecast(Db.s(a, "asset_type"), Db.d(a, "condition_score"), Db.i(a, "year_built"),
                Db.i(a, "traffic_aadt"), Db.d(a, "replacement_value_cr")));
        List<Map<String, Object>> insp = new ArrayList<>();
        for (var i : Db.query("SELECT * FROM inspections WHERE asset_id = ? ORDER BY date DESC, id DESC", id))
            insp.add(M.of("id", i.get("id"), "date", i.get("date"), "inspector", i.get("inspector"), "score", i.get("condition_score"),
                    "defects", i.get("defects"), "action", i.get("action"), "remarks", i.get("remarks")));
        out.put("inspections", insp);
        List<Map<String, Object>> projs = new ArrayList<>();
        for (var p : Db.query("SELECT * FROM projects WHERE asset_id = ? ORDER BY id", id))
            projs.add(M.of("id", p.get("id"), "code", p.get("code"), "name", p.get("name"), "status", p.get("status"),
                    "progress", p.get("physical_progress"), "cost_cr", p.get("sanctioned_cost_cr")));
        out.put("projects", projs);
        return out;
    }
}
