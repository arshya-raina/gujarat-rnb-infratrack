import { useState, useEffect } from "react";
import { useLocation, useNavigate, Link } from "react-router-dom";
import { useApi, useAuth, fmt, crore, dateIN, isOfficer } from "../lib.jsx";
import { Panel, Loading, ErrorBox, Pager, Select, Progress, Status } from "../components/ui.jsx";

const STATUSES = [["in_progress", "In progress"], ["sanctioned", "Sanctioned"], ["tendering", "Tendering"], ["on_hold", "On hold"], ["completed", "Completed"]];

export default function Projects() {
  const { user } = useAuth();
  const { search } = useLocation();
  const nav = useNavigate();
  const init = Object.fromEntries(new URLSearchParams(search));
  const [f, setF] = useState(init);
  const [q, setQ] = useState("");
  const [page, setPage] = useState(1);
  const officer = isOfficer(user);
  const opts = useApi(officer ? "/api/hierarchy/options" : null);

  useEffect(() => { setF(Object.fromEntries(new URLSearchParams(search))); setPage(1); }, [search]);
  useEffect(() => { const t = setTimeout(() => { setF((x) => ({ ...x, q })); setPage(1); }, 350); return () => clearTimeout(t); }, [q]);

  const res = useApi("/api/projects", { ...f, page, page_size: 20 });
  const set = (k) => (v) => { setF((x) => ({ ...x, [k]: v })); setPage(1); };

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="display">{officer ? "Projects & works" : "My awarded works"}</h1>
          <p className="muted">
            {officer
              ? "Every sanctioned work in your jurisdiction, from tendering to completion. Works more than 10 points behind their planned progress are flagged."
              : `Works awarded to ${user.contractor_name}. Open a work to report progress or submit a running account bill.`}
          </p>
        </div>
      </header>

      {res.data && (
        <div className="strip">
          <div><b>{fmt(res.data.total)}</b><span>works</span></div>
          <div><b>{crore(res.data.totals.sanctioned_cr)}</b><span>sanctioned</span></div>
          <div><b>{crore(res.data.totals.spent_cr)}</b><span>spent</span></div>
          <div><b>{fmt(res.data.totals.avg_progress, 1)}%</b><span>average progress</span></div>
        </div>
      )}

      <Panel className="flush">
        <div className="filters">
          <label className="field grow">
            <span>Search</span>
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Work name, code or scheme" />
          </label>
          <Select label="Status" value={f.status} onChange={set("status")} options={STATUSES.map(([v, l]) => ({ value: v, label: l }))} />
          {opts.data && opts.data.regions.length > 1 && (
            <Select label="Circle" value={f.region_id} onChange={set("region_id")} options={opts.data.regions.map((r) => ({ value: r.id, label: r.name }))} />
          )}
          <label className="check">
            <input type="checkbox" checked={!!f.delayed} onChange={(e) => set("delayed")(e.target.checked ? 1 : "")} />
            Behind schedule only
          </label>
        </div>
        <ErrorBox error={res.error} />
        {!res.data ? <Loading /> : (
          <>
            <div className="table-scroll">
              <table className="table">
                <thead>
                  <tr><th>Work</th>{officer && <th>Contractor</th>}<th className="num">Sanctioned</th><th>Physical progress</th><th>Target</th><th>Status</th></tr>
                </thead>
                <tbody>
                  {res.data.items.map((p) => (
                    <tr key={p.id} className="clickable" onClick={() => nav(`/projects/${p.id}`)}>
                      <td><Link to={`/projects/${p.id}`} onClick={(e) => e.stopPropagation()}>{p.name}</Link><div className="sub">{p.code}, {p.scheme}, {p.region}</div></td>
                      {officer && <td className="sub-cell">{p.contractor || "Not yet awarded"}</td>}
                      <td className="num">{crore(p.cost_cr)}</td>
                      <td style={{ minWidth: 130 }}><Progress value={p.progress} tone={p.delayed ? "warn" : ""} /><span className="sub">{fmt(p.progress, 1)}%</span></td>
                      <td>{dateIN(p.target_date)}</td>
                      <td>{p.delayed ? <span className="status status-delayed">Behind schedule</span> : <Status value={p.status} />}</td>
                    </tr>
                  ))}
                  {!res.data.items.length && <tr><td colSpan={6} className="empty">No works match these filters.</td></tr>}
                </tbody>
              </table>
            </div>
            <Pager page={page} pageSize={20} total={res.data.total} onPage={setPage} />
          </>
        )}
      </Panel>
    </div>
  );
}
