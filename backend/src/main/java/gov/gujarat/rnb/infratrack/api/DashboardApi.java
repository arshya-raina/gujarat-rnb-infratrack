package gov.gujarat.rnb.infratrack.api;

import gov.gujarat.rnb.infratrack.core.*;
import gov.gujarat.rnb.infratrack.db.Db;
import gov.gujarat.rnb.infratrack.db.Where;
import gov.gujarat.rnb.infratrack.http.ApiException;
import gov.gujarat.rnb.infratrack.http.Router;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Command center dashboard and the Circle → Division → Sub-Division drill-down. */
public final class DashboardApi {
    private DashboardApi() {}

    private static final List<String> LEVELS = List.of("state", "region", "division", "subdivision");

    public static void register(Router r) {
        r.get("/api/dashboard/summary", req -> {
            User u = Auth.officer(req);
            Map<String, Object> s = Stats.compute(Scope.assets(u), Scope.projects(u), Scope.complaints(u));
            s.put("scope", Scope.label(u));
            s.put("root", M.of("level", Scope.rootLevel(u), "id", Scope.rootId(u)));
            return s;
        });

        r.get("/api/dashboard/alerts", req -> {
            User u = Auth.officer(req);
            Where a = Scope.assets(u), p = Scope.projects(u), c = Scope.complaints(u);
            LocalDate today = LocalDate.now();
            List<Map<String, Object>> crit = new ArrayList<>();
            for (var x : Db.query("SELECT * FROM assets WHERE " + a.sql() + " AND condition = 'critical' ORDER BY risk_score DESC LIMIT 8", a.args()))
                crit.add(M.of("id", x.get("id"), "code", x.get("asset_code"), "name", x.get("name"), "type", x.get("asset_type"),
                        "score", x.get("condition_score"), "risk", x.get("risk_score"), "status", x.get("status"), "lat", x.get("lat"), "lng", x.get("lng")));
            List<Map<String, Object>> delayed = new ArrayList<>();
            for (var x : Db.query("SELECT * FROM projects WHERE " + p.sql() + " AND status = 'in_progress' AND is_delayed ORDER BY sanctioned_cost_cr DESC LIMIT 8", p.args()))
                delayed.add(M.of("id", x.get("id"), "code", x.get("code"), "name", x.get("name"), "progress", x.get("physical_progress"),
                        "cost_cr", x.get("sanctioned_cost_cr"), "days_left", ChronoUnit.DAYS.between(today, Db.date(x, "target_date"))));
            List<Map<String, Object>> comp = new ArrayList<>();
            for (var x : Db.query("SELECT * FROM complaints WHERE " + c.sql() + " AND status <> 'resolved' AND priority = 'high' ORDER BY created_at DESC LIMIT 6", c.args()))
                comp.add(M.of("id", x.get("id"), "ticket", x.get("ticket"), "category", x.get("category"), "location", x.get("location_text"),
                        "status", x.get("status"), "created_at", x.get("created_at")));
            return M.of("critical_assets", crit, "delayed_projects", delayed, "urgent_complaints", comp);
        });

        r.get("/api/dashboard/critical-map", req -> {
            Where a = Scope.assets(Auth.officer(req));
            return Db.query("SELECT id, name, asset_type, lat, lng, condition_score FROM assets WHERE " + a.sql() + " AND condition = 'critical'", a.args());
        });

        r.get("/api/activity", req -> {
            User u = Auth.officer(req);
            int limit = req.qInt("limit", 15, 1, 100);
            List<Map<String, Object>> rows = u.is("state_admin")
                    ? Db.query("SELECT * FROM audit_logs ORDER BY ts DESC LIMIT ?", limit)
                    : Db.query("SELECT * FROM audit_logs WHERE region_id = ? OR region_id IS NULL ORDER BY ts DESC LIMIT ?", u.regionId(), limit);
            List<Map<String, Object>> out = new ArrayList<>();
            for (var l : rows)
                out.add(M.of("ts", l.get("ts"), "user", l.get("user_name"), "action", l.get("action"), "entity", l.get("entity"),
                        "entity_id", l.get("entity_id"), "detail", l.get("detail")));
            return out;
        });

        r.get("/api/hierarchy/node", req -> node(Auth.officer(req), Optional.ofNullable(req.q("level")).orElse("state"), req.qInt("id")));

        r.get("/api/hierarchy/options", req -> {
            User u = Auth.officer(req);
            List<Map<String, Object>> regs = new ArrayList<>(), divs = new ArrayList<>(), subs = new ArrayList<>();
            for (var x : Hierarchy.regions())
                if (Scope.canAccess(u, x.id(), null, null) || (u.is("division_engineer", "subdivision_engineer") && Objects.equals(x.id(), u.regionId())))
                    regs.add(M.of("id", x.id(), "name", x.name()));
            for (var x : Hierarchy.divisions())
                if (Scope.canAccess(u, x.regionId(), x.id(), null) || (u.is("subdivision_engineer") && Objects.equals(x.id(), u.divisionId())))
                    divs.add(M.of("id", x.id(), "name", x.name(), "region_id", x.regionId()));
            for (var x : Hierarchy.subdivisions())
                if (Scope.canAccess(u, x.regionId(), x.divisionId(), x.id()))
                    subs.add(M.of("id", x.id(), "name", x.name(), "division_id", x.divisionId()));
            return M.of("regions", regs, "divisions", divs, "subdivisions", subs);
        });
    }

    private static Map<String, Object> node(User u, String level, Integer id) {
        List<Map<String, Object>> crumbs = new ArrayList<>();
        crumbs.add(M.of("level", "state", "id", null, "name", "Gujarat"));
        Map<String, Object> info;
        Where a = new Where(), p = new Where(), c = new Where();
        String childLevel;
        List<Map<String, Object>> kids = new ArrayList<>();
        List<Object[]> children = new ArrayList<>(); // {id, name, lat, lng, extra map}

        switch (level) {
            case "state" -> {
                if (!u.is("state_admin")) throw ApiException.forbidden("State-wide view is limited to the State Administrator");
                info = M.of("level", "state", "id", null, "name", "Gujarat", "subtitle", "Roads & Buildings Department", "lat", 22.6, "lng", 71.6);
                childLevel = "region";
                for (var x : Hierarchy.regions())
                    children.add(new Object[]{x.id(), x.name(), x.lat(), x.lng(), M.of("is_focus", x.isFocus(), "circle_name", x.circleName())});
            }
            case "region" -> {
                var x = Hierarchy.region(id);
                if (x == null) throw ApiException.notFound("Circle");
                Scope.ensure(u, x.id(), null, null);
                info = M.of("level", "region", "id", x.id(), "name", x.name(), "subtitle", x.circleName(), "lat", x.lat(), "lng", x.lng());
                crumbs.add(M.of("level", "region", "id", x.id(), "name", x.name()));
                a.and("region_id = ?", x.id()); p.and("region_id = ?", x.id()); c.and("region_id = ?", x.id());
                childLevel = "division";
                for (var d : Hierarchy.divisionsOf(x.id())) children.add(new Object[]{d.id(), d.name(), d.lat(), d.lng(), null});
            }
            case "division" -> {
                var d = Hierarchy.division(id);
                if (d == null) throw ApiException.notFound("Division");
                Scope.ensure(u, d.regionId(), d.id(), null);
                var reg = Hierarchy.region(d.regionId());
                info = M.of("level", "division", "id", d.id(), "name", d.name(), "subtitle", reg.circleName(), "lat", d.lat(), "lng", d.lng());
                crumbs.add(M.of("level", "region", "id", reg.id(), "name", reg.name()));
                crumbs.add(M.of("level", "division", "id", d.id(), "name", d.name()));
                a.and("division_id = ?", d.id()); p.and("division_id = ?", d.id()); c.and("division_id = ?", d.id());
                childLevel = "subdivision";
                for (var s : Hierarchy.subdivisionsOf(d.id())) children.add(new Object[]{s.id(), s.name(), s.lat(), s.lng(), null});
            }
            case "subdivision" -> {
                var s = Hierarchy.subdivision(id);
                if (s == null) throw ApiException.notFound("Sub-division");
                Scope.ensure(u, s.regionId(), s.divisionId(), s.id());
                var d = Hierarchy.division(s.divisionId());
                var reg = Hierarchy.region(s.regionId());
                info = M.of("level", "subdivision", "id", s.id(), "name", s.name() + " Sub-Division", "subtitle", d.name(), "lat", s.lat(), "lng", s.lng());
                crumbs.add(M.of("level", "region", "id", reg.id(), "name", reg.name()));
                crumbs.add(M.of("level", "division", "id", d.id(), "name", d.name()));
                crumbs.add(M.of("level", "subdivision", "id", s.id(), "name", s.name()));
                a.and("subdivision_id = ?", s.id()); p.and("subdivision_id = ?", s.id()); c.and("subdivision_id = ?", s.id());
                childLevel = null;
            }
            default -> throw ApiException.bad("Unknown level");
        }

        // hide breadcrumb levels above the user's own jurisdiction
        int rootIdx = LEVELS.indexOf(Scope.rootLevel(u));
        crumbs.removeIf(cr -> LEVELS.indexOf((String) cr.get("level")) < rootIdx);

        Map<String, Object> stats = Stats.compute(a, p, c);
        if (childLevel != null) {
            var cs = Stats.children(childLevel, a, p);
            for (Object[] ch : children) {
                Map<String, Object> row = M.of("level", childLevel, "id", ch[0], "name", ch[1], "lat", ch[2], "lng", ch[3]);
                row.putAll(cs.getOrDefault((Integer) ch[0], Stats.emptyChild()));
                if (ch[4] != null) row.putAll(castMap(ch[4]));
                kids.add(row);
            }
        }
        return M.of("node", info, "breadcrumb", crumbs, "stats", stats, "child_level", childLevel, "children", kids);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) { return (Map<String, Object>) o; }
}
