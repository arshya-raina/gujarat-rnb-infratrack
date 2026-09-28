import { useState, useEffect } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useApi, fmt, dateIN, TYPE_ONE, TYPES, CONDITIONS } from "../lib.jsx";
import { CondPill, Loading, ErrorBox, Pager, Select, Status } from "../components/ui.jsx";

const SORTS = [
  { value: "risk", label: "Highest risk first" },
  { value: "condition", label: "Worst condition first" },
  { value: "due", label: "Maintenance date" },
  { value: "age", label: "Oldest first" },
  { value: "value", label: "Highest value" },
  { value: "name", label: "Name" },
];

/** fixed: filters that cannot be changed here; initial: starting filters; scope: dropdown options for jurisdiction */
export default function AssetTable({ fixed = {}, initial = {}, scope, onFiltersChange }) {
  const nav = useNavigate();
  const [f, setF] = useState({ sort: "risk", ...initial });
  const [page, setPage] = useState(1);
  const [q, setQ] = useState(initial.q || "");

  useEffect(() => {
    const t = setTimeout(() => setF((x) => (x.q === q ? x : { ...x, q })), 350);
    return () => clearTimeout(t);
  }, [q]);
  useEffect(() => { setPage(1); onFiltersChange?.(f); }, [JSON.stringify(f)]); // eslint-disable-line react-hooks/exhaustive-deps

  const res = useApi("/api/assets", { ...f, ...fixed, page, page_size: 20 });
  const set = (k) => (v) => setF((x) => {
    const n = { ...x, [k]: v };
    if (k === "region_id") { delete n.division_id; delete n.subdivision_id; }
    if (k === "division_id") delete n.subdivision_id;
    return n;
  });

  const divisions = scope?.divisions.filter((d) => !f.region_id || d.region_id === Number(f.region_id)) || [];
  const subs = scope?.subdivisions.filter((s) => (!f.division_id || s.division_id === Number(f.division_id)) && (!f.region_id || divisions.some((d) => d.id === s.division_id))) || [];

  return (
    <div className="asset-table">
      <div className="filters">
        <label className="field grow">
          <span>Search</span>
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Name, asset code or category" />
        </label>
        {scope && scope.regions.length > 1 && (
          <Select label="Circle" value={f.region_id} onChange={set("region_id")} options={scope.regions.map((r) => ({ value: r.id, label: r.name }))} />
        )}
        {scope && divisions.length > 1 && (
          <Select label="Division" value={f.division_id} onChange={set("division_id")} options={divisions.map((r) => ({ value: r.id, label: r.name }))} />
        )}
        {scope && subs.length > 1 && (
          <Select label="Sub-division" value={f.subdivision_id} onChange={set("subdivision_id")} options={subs.map((r) => ({ value: r.id, label: r.name }))} />
        )}
        <Select label="Type" value={f.asset_type} onChange={set("asset_type")} options={TYPES.map((t) => ({ value: t, label: TYPE_ONE[t] }))} />
        <Select label="Condition" value={f.condition} onChange={set("condition")} options={CONDITIONS.map((c) => ({ value: c, label: c[0].toUpperCase() + c.slice(1) }))} />
        <Select label="Sort" value={f.sort} onChange={set("sort")} options={SORTS} all={null} />
        <label className="check">
          <input type="checkbox" checked={!!f.due} onChange={(e) => set("due")(e.target.checked ? 1 : "")} />
          Maintenance due only
        </label>
      </div>
      <ErrorBox error={res.error} />
      {!res.data ? <Loading /> : (
        <>
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr><th>Asset</th><th>Type</th><th>Condition</th><th className="num">Risk</th><th>Next maintenance</th><th>Status</th></tr>
              </thead>
              <tbody>
                {res.data.items.map((a) => {
                  const overdue = new Date(a.next_maintenance) <= new Date();
                  return (
                    <tr key={a.id} className="clickable" onClick={() => nav(`/assets/${a.id}`)}>
                      <td><Link to={`/assets/${a.id}`} onClick={(e) => e.stopPropagation()}>{a.name}</Link><div className="sub">{a.code}, built {a.year_built}</div></td>
                      <td className="sub-cell">{a.category}</td>
                      <td><CondPill condition={a.condition} score={a.score} /></td>
                      <td className="num"><RiskMeter v={a.risk} /></td>
                      <td className={overdue ? "text-danger" : ""}>{dateIN(a.next_maintenance)}{overdue && <div className="sub">Overdue</div>}</td>
                      <td><Status value={a.status} /></td>
                    </tr>
                  );
                })}
                {!res.data.items.length && <tr><td colSpan={6} className="empty">No assets match these filters. Clear a filter to widen the search.</td></tr>}
              </tbody>
            </table>
          </div>
          <Pager page={page} pageSize={20} total={res.data.total} onPage={setPage} />
        </>
      )}
    </div>
  );
}

export function RiskMeter({ v }) {
  const tone = v >= 60 ? "high" : v >= 40 ? "mid" : "low";
  return <span className={`risk risk-${tone}`}>{fmt(v, 0)}</span>;
}
