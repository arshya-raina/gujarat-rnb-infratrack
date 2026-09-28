package gov.gujarat.rnb.infratrack.api;

import gov.gujarat.rnb.infratrack.core.*;
import gov.gujarat.rnb.infratrack.db.Db;
import gov.gujarat.rnb.infratrack.db.Where;
import gov.gujarat.rnb.infratrack.http.ApiException;
import gov.gujarat.rnb.infratrack.http.Router;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Works monitoring: projects, contractor progress reports and running account bills. */
public final class ProjectApi {
    private ProjectApi() {}

    private static final String[] PROJECT_ROLES = {"state_admin", "regional_officer", "division_engineer", "subdivision_engineer", "contractor"};
    private static final String[] APPROVERS = {"division_engineer", "regional_officer", "state_admin"};

    public static void register(Router r) {
        r.get("/api/projects", req -> {
            User u = Auth.require(req, PROJECT_ROLES);
            Where w = Scope.projects(u);
            Integer region = req.qInt("region_id"), division = req.qInt("division_id"), sub = req.qInt("subdivision_id");
            w.andIf(req.q("status") != null, "status = ?", req.q("status"));
            w.andIf(region != null, "region_id = ?", region);
            w.andIf(division != null, "division_id = ?", division);
            w.andIf(sub != null, "subdivision_id = ?", sub);
            w.andIf(req.qBool("delayed"), "is_delayed");
            String q = req.q("q");
            if (q != null) {
                String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                w.and("(LOWER(name) LIKE ? OR LOWER(code) LIKE ? OR LOWER(scheme) LIKE ?)", like, like, like);
            }
            int page = req.qInt("page", 1, 1, 100000), size = req.qInt("page_size", 25, 1, 100);
            long total = Db.count("SELECT COUNT(id) FROM projects WHERE " + w.sql(), w.args());
            var agg = Db.one("SELECT SUM(sanctioned_cost_cr) AS sc, SUM(expenditure_cr) AS sp, AVG(physical_progress) AS ap FROM projects WHERE " + w.sql(), w.args());
            Map<Integer, String> contractors = contractorNames();
            List<Map<String, Object>> items = new ArrayList<>();
            for (var p : Db.query("SELECT * FROM projects WHERE " + w.sql() + " ORDER BY is_delayed DESC, sanctioned_cost_cr DESC, id LIMIT ? OFFSET ?",
                    w.args(size, (page - 1) * size)))
                items.add(row(p, contractors));
            return M.of("total", total, "page", page, "page_size", size,
                    "totals", M.of("sanctioned_cr", M.round(Db.d(agg, "sc"), 1), "spent_cr", M.round(Db.d(agg, "sp"), 1), "avg_progress", M.round(Db.d(agg, "ap"), 1)),
                    "items", items);
        });

        r.get("/api/projects/{id}", req -> detail(Auth.require(req, PROJECT_ROLES), req.paramInt("id")));

        r.post("/api/projects/{id}/progress", req -> {
            User u = Auth.require(req, PROJECT_ROLES);
            int id = req.paramInt("id");
            var p = load(u, id);
            String status = Db.s(p, "status");
            if (!status.equals("in_progress") && !status.equals("on_hold"))
                throw ApiException.bad("Progress can only be reported on works that have started");
            double prog = req.num("physical_progress", "Physical progress", 0, 100);
            double last = Db.d(p, "physical_progress");
            if (prog < last) throw ApiException.bad("Progress cannot go below the last reported " + M.round(last, 1) + "%");
            String newStatus = prog >= 100 ? "completed" : "in_progress";
            boolean delayed = Health.isDelayed(newStatus, prog, Db.date(p, "start_date"), Db.date(p, "target_date"), LocalDate.now());
            Db.update("UPDATE projects SET physical_progress = ?, status = ?, is_delayed = ? WHERE id = ?", prog, newStatus, delayed, id);
            Db.update("INSERT INTO progress_updates (project_id, date, physical_progress, remarks, submitted_by) VALUES (?,?,?,?,?)",
                    id, LocalDate.now(), prog, req.str("remarks", ""), u.name() + ", " + u.designation());
            Stats.audit(u.name(), "Progress update", "project", p.get("code"), String.format("%.0f%% — %s", prog, p.get("name")), Db.i(p, "region_id"));
            return detail(u, id);
        });

        r.post("/api/projects/{id}/bills", req -> {
            User u = Auth.require(req, "contractor");
            int id = req.paramInt("id");
            var p = load(u, id);
            double amount = req.num("amount_lakh", "Amount", 0.01, 1_000_000);
            long n = Db.count("SELECT COUNT(id) FROM bills WHERE project_id = ?", id);
            Db.update("INSERT INTO bills (project_id, bill_no, amount_lakh, submitted_on, status, remarks) VALUES (?,?,?,?,?,?)",
                    id, "RA-" + (n + 1), amount, LocalDate.now(), "submitted", req.str("remarks", ""));
            Stats.audit(u.name(), "RA bill submitted", "bill", p.get("code"), String.format("₹%.2f lakh — %s", amount, p.get("name")), Db.i(p, "region_id"));
            return detail(u, id);
        });

        r.patch("/api/projects/bills/{id}", req -> {
            User u = Auth.require(req, APPROVERS);
            String status = req.str("status", "");
            if (!Set.of("verified", "approved", "rejected", "paid").contains(status)) throw ApiException.bad("Unknown bill status");
            var b = Db.one("SELECT * FROM bills WHERE id = ?", req.paramInt("id"));
            if (b == null) throw ApiException.notFound("Bill");
            int pid = Db.i(b, "project_id");
            var p = load(u, pid);
            Db.update("UPDATE bills SET status = ?, remarks = ? WHERE id = ?", status, req.str("remarks", ""), b.get("id"));
            if (status.equals("paid"))
                Db.update("UPDATE projects SET expenditure_cr = ROUND(expenditure_cr + ?, 2) WHERE id = ?", Db.d(b, "amount_lakh") / 100, pid);
            Stats.audit(u.name(), "RA bill " + status, "bill", p.get("code"), String.format("%s ₹%.2f lakh", b.get("bill_no"), Db.d(b, "amount_lakh")), Db.i(p, "region_id"));
            return detail(u, pid);
        });
    }

    /** Loads a project and checks the user may act on it. */
    private static Map<String, Object> load(User u, int id) {
        var p = Db.one("SELECT * FROM projects WHERE id = ?", id);
        if (p == null) throw ApiException.notFound("Project");
        if (u.is("contractor")) {
            if (!Objects.equals(Db.iN(p, "contractor_id"), u.contractorId())) throw ApiException.forbidden("This work is not awarded to your firm");
        } else if (!Scope.canAccess(u, Db.i(p, "region_id"), Db.i(p, "division_id"), Db.i(p, "subdivision_id"))) {
            throw ApiException.forbidden("This project is outside your jurisdiction");
        }
        return p;
    }

    private static Map<Integer, String> contractorNames() {
        Map<Integer, String> m = new HashMap<>();
        for (var c : Db.query("SELECT id, name FROM contractors")) m.put(Db.i(c, "id"), Db.s(c, "name"));
        return m;
    }

    private static Map<String, Object> row(Map<String, Object> p, Map<Integer, String> contractors) {
        Integer cid = Db.iN(p, "contractor_id");
        return M.of("id", p.get("id"), "code", p.get("code"), "name", p.get("name"), "work_type", p.get("work_type"),
                "scheme", p.get("scheme"), "status", p.get("status"), "delayed", p.get("is_delayed"), "progress", p.get("physical_progress"),
                "cost_cr", p.get("sanctioned_cost_cr"), "spent_cr", p.get("expenditure_cr"), "start_date", p.get("start_date"),
                "target_date", p.get("target_date"), "contractor", cid == null ? null : contractors.get(cid),
                "region", Hierarchy.region(Db.i(p, "region_id")).name());
    }

    private static Map<String, Object> detail(User u, int id) {
        var p = load(u, id);
        LocalDate today = LocalDate.now(), start = Db.date(p, "start_date"), target = Db.date(p, "target_date");
        Map<String, Object> out = row(p, contractorNames());
        out.put("division", Hierarchy.division(Db.i(p, "division_id")).name());
        out.put("subdivision", Hierarchy.subdivision(Db.i(p, "subdivision_id")).name());
        out.put("expected_progress", "in_progress".equals(p.get("status")) ? M.round(Health.expectedProgress(start, target, today), 1) : null);
        out.put("days_left", ChronoUnit.DAYS.between(today, target));
        Integer cid = Db.iN(p, "contractor_id");
        var c = cid == null ? null : Db.one("SELECT * FROM contractors WHERE id = ?", cid);
        out.put("contractor_detail", c == null ? null : M.of("name", c.get("name"), "registration_no", c.get("registration_no"),
                "class", c.get("contractor_class"), "rating", c.get("rating")));
        Integer aid = Db.iN(p, "asset_id");
        var a = aid == null ? null : Db.one("SELECT * FROM assets WHERE id = ?", aid);
        out.put("asset", a == null ? null : M.of("id", a.get("id"), "code", a.get("asset_code"), "name", a.get("name"),
                "condition", a.get("condition"), "score", a.get("condition_score")));
        List<Map<String, Object>> ups = new ArrayList<>();
        for (var x : Db.query("SELECT * FROM progress_updates WHERE project_id = ? ORDER BY date DESC, id DESC", id))
            ups.add(M.of("id", x.get("id"), "date", x.get("date"), "progress", x.get("physical_progress"), "remarks", x.get("remarks"), "by", x.get("submitted_by")));
        out.put("updates", ups);
        List<Map<String, Object>> bills = new ArrayList<>();
        for (var x : Db.query("SELECT * FROM bills WHERE project_id = ? ORDER BY id", id))
            bills.add(M.of("id", x.get("id"), "bill_no", x.get("bill_no"), "amount_lakh", x.get("amount_lakh"),
                    "submitted_on", x.get("submitted_on"), "status", x.get("status"), "remarks", x.get("remarks")));
        out.put("bills", bills);
        return out;
    }
}
