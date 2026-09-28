package gov.gujarat.rnb.infratrack.core;

import gov.gujarat.rnb.infratrack.db.Db;
import gov.gujarat.rnb.infratrack.db.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Aggregations shared by the dashboard and the hierarchy explorer. */
public final class Stats {
    private Stats() {}

    public static Map<String, Object> compute(Where a, Where p, Where c) {
        LocalDate today = LocalDate.now();
        var row = Db.one("SELECT COUNT(id) AS tot, SUM(CASE WHEN condition = 'critical' THEN 1 ELSE 0 END) AS crit, "
                + "SUM(CASE WHEN next_maintenance_date <= ? THEN 1 ELSE 0 END) AS due, AVG(condition_score) AS avg, "
                + "SUM(replacement_value_cr) AS val FROM assets WHERE " + a.sql(), prepend(today, a.args()));

        Map<String, Object> byType = new LinkedHashMap<>();
        for (var r : Db.query("SELECT asset_type, COUNT(*) AS n FROM assets WHERE " + a.sql() + " GROUP BY asset_type", a.args()))
            byType.put(Db.s(r, "asset_type"), Db.l(r, "n"));
        Map<String, Object> byCond = new LinkedHashMap<>();
        for (var r : Db.query("SELECT condition, COUNT(*) AS n FROM assets WHERE " + a.sql() + " GROUP BY condition", a.args()))
            byCond.put(Db.s(r, "condition"), Db.l(r, "n"));

        Map<String, Object> projects = new LinkedHashMap<>();
        for (var r : Db.query("SELECT status, COUNT(*) AS n, SUM(sanctioned_cost_cr) AS sc, SUM(expenditure_cr) AS sp FROM projects WHERE "
                + p.sql() + " GROUP BY status", p.args()))
            projects.put(Db.s(r, "status"), M.of("count", Db.l(r, "n"), "sanctioned_cr", M.round(Db.d(r, "sc"), 1), "spent_cr", M.round(Db.d(r, "sp"), 1)));
        long delayed = Db.count("SELECT COUNT(*) FROM projects WHERE " + p.sql() + " AND status = 'in_progress' AND is_delayed", p.args());
        long openComplaints = Db.count("SELECT COUNT(*) FROM complaints WHERE " + c.sql() + " AND status <> 'resolved'", c.args());

        @SuppressWarnings("unchecked")
        Map<String, Object> active = (Map<String, Object>) projects.getOrDefault("in_progress", M.of("count", 0L, "sanctioned_cr", 0.0, "spent_cr", 0.0));
        return M.of(
                "total_assets", Db.l(row, "tot"),
                "active_projects", active.get("count"),
                "maintenance_due", Db.l(row, "due"),
                "critical_assets", Db.l(row, "crit"),
                "avg_condition", M.round(Db.d(row, "avg"), 1),
                "asset_value_cr", Math.round(Db.d(row, "val")),
                "delayed_projects", delayed,
                "open_complaints", openComplaints,
                "active_budget_cr", active.get("sanctioned_cr"),
                "active_spent_cr", active.get("spent_cr"),
                "by_type", byType,
                "by_condition", byCond,
                "projects_by_status", projects);
    }

    /** Aggregates per child node (grouped by region_id / division_id / subdivision_id). */
    public static Map<Integer, Map<String, Object>> children(String level, Where a, Where p) {
        String col = level + "_id";
        LocalDate today = LocalDate.now();
        Map<Integer, Long> active = new HashMap<>();
        for (var r : Db.query("SELECT " + col + " AS k, COUNT(*) AS n FROM projects WHERE " + p.sql() + " AND status = 'in_progress' GROUP BY " + col, p.args()))
            active.put(Db.i(r, "k"), Db.l(r, "n"));
        Map<Integer, Map<String, Object>> out = new HashMap<>();
        for (var r : Db.query("SELECT " + col + " AS k, COUNT(id) AS n, SUM(CASE WHEN condition = 'critical' THEN 1 ELSE 0 END) AS crit, "
                + "SUM(CASE WHEN next_maintenance_date <= ? THEN 1 ELSE 0 END) AS due, AVG(condition_score) AS avg FROM assets WHERE "
                + a.sql() + " GROUP BY " + col, prepend(today, a.args()))) {
            int k = Db.i(r, "k");
            out.put(k, M.of("asset_count", Db.l(r, "n"), "critical", Db.l(r, "crit"), "maintenance_due", Db.l(r, "due"),
                    "avg_condition", M.round(Db.d(r, "avg"), 1), "active_projects", active.getOrDefault(k, 0L)));
        }
        return out;
    }

    public static Map<String, Object> emptyChild() {
        return M.of("asset_count", 0, "critical", 0, "maintenance_due", 0, "avg_condition", 0, "active_projects", 0);
    }

    public static Object[] prepend(Object first, Object[] rest) {
        Object[] all = new Object[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }

    public static void audit(String userName, String action, String entity, Object entityId, String detail, Integer regionId) {
        Db.update("INSERT INTO audit_logs (ts, user_name, action, entity, entity_id, detail, region_id) VALUES (?,?,?,?,?,?,?)",
                LocalDateTime.now().withNano(0), userName, action, entity, String.valueOf(entityId), detail, regionId);
    }
}
