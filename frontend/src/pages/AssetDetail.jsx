import { useState } from "react";
import { useParams, Link } from "react-router-dom";
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, ReferenceLine, CartesianGrid } from "recharts";
import { useApi, api, useAuth, fmt, dateIN, crore, TYPE_ONE, COND_COLOR } from "../lib.jsx";
import { Panel, Loading, ErrorBox, CondPill, Status, Progress } from "../components/ui.jsx";
import MapView from "../components/MapView.jsx";
import { RiskMeter } from "./AssetTable.jsx";

export default function AssetDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const res = useApi(`/api/assets/${id}`);
  const [form, setForm] = useState({ condition_score: "", defects: "", remarks: "", status: "" });
  const [msg, setMsg] = useState(null);
  const [busy, setBusy] = useState(false);

  if (res.error) return <div className="page"><ErrorBox error={res.error} /></div>;
  if (!res.data) return <div className="page"><Loading label="Loading asset" /></div>;
  const a = res.data;
  const f = a.forecast;

  const hist = [...a.inspections].reverse().map((i) => {
    const d = new Date(i.date);
    return { x: +(d.getFullYear() + d.getMonth() / 12).toFixed(2), recorded: i.score };
  });
  const fc = f.curve.map((c) => ({ x: c.year + (c.year === f.curve[0].year ? new Date().getMonth() / 12 : 0), predicted: c.predicted }));
  const xs = [...hist, ...fc].map((p) => p.x);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setMsg(null);
    try {
      const out = await api(`/api/assets/${id}/inspections`, {
        method: "POST",
        body: { condition_score: Number(form.condition_score), defects: form.defects, remarks: form.remarks, status: form.status || null },
      });
      res.setData(out);
      setForm({ condition_score: "", defects: "", remarks: "", status: "" });
      setMsg({ ok: true, text: `Inspection saved. Condition is now ${out.condition} and next maintenance is ${dateIN(out.next_maintenance)}.` });
    } catch (x) {
      setMsg({ ok: false, text: x.message });
    } finally {
      setBusy(false);
    }
  };

  const score = Number(form.condition_score);
  const preview = form.condition_score === "" ? null : score < 25 ? "critical" : score < 50 ? "poor" : score < 75 ? "fair" : "good";

  return (
    <div className="page">
      <nav className="crumbs">
        <span><Link to={`/explore/region/${a.region_id}`}>{a.region}</Link></span>
        <span><Link to={`/explore/division/${a.division_id}`}>{a.division}</Link></span>
        <span><Link to={`/explore/subdivision/${a.subdivision_id}`}>{a.subdivision}</Link></span>
        <span><b>{a.code}</b></span>
      </nav>
      <header className="page-head">
        <div>
          <p className="level-tag">{TYPE_ONE[a.type]}, {a.category}</p>
          <h1 className="display">{a.name}</h1>
          <div className="pill-row">
            <CondPill condition={a.condition} score={a.score} />
            <Status value={a.status} />
            <span className="muted">Risk index <RiskMeter v={a.risk} /></span>
          </div>
        </div>
      </header>

      <div className="grid-detail">
        <div className="col">
          <Panel title="Health forecast">
            <div className="forecast-head">
              <div className={`action-card cond-bg-${a.condition}`}>
                <span className="muted small">Recommended action</span>
                <strong>{f.recommended_action}</strong>
                <span>Estimated cost ₹{fmt(f.estimated_cost_lakh, 1)} lakh</span>
              </div>
              <dl className="facts compact">
                <div><dt>Wear rate</dt><dd>{f.deterioration_per_year} points a year</dd></div>
                <div><dt>Reaches critical</dt><dd>{f.months_to_critical === 0 ? "Already critical" : f.months_to_critical > 120 ? "Beyond 10 years" : `In about ${f.months_to_critical} months`}</dd></div>
              </dl>
            </div>
            <ResponsiveContainer width="100%" height={230}>
              <LineChart margin={{ top: 10, right: 16, left: -12, bottom: 0 }}>
                <CartesianGrid stroke="#E4E9E5" vertical={false} />
                <XAxis type="number" dataKey="x" domain={[Math.floor(Math.min(...xs)), Math.ceil(Math.max(...xs))]} tickFormatter={(v) => Math.floor(v)} allowDuplicatedCategory={false} tick={{ fontSize: 12 }} />
                <YAxis domain={[0, 100]} tick={{ fontSize: 12 }} />
                <Tooltip formatter={(v, n) => [fmt(v, 1), n === "recorded" ? "Inspected" : "Forecast"]} labelFormatter={(v) => Math.floor(v)} />
                <ReferenceLine y={25} stroke={COND_COLOR.critical} strokeDasharray="4 4" label={{ value: "Critical", position: "insideTopRight", fill: COND_COLOR.critical, fontSize: 12 }} />
                <ReferenceLine y={50} stroke={COND_COLOR.poor} strokeDasharray="2 6" />
                <Line data={hist} dataKey="recorded" stroke="#0B6E4F" strokeWidth={2.5} dot={{ r: 4 }} isAnimationActive={false} />
                <Line data={fc} dataKey="predicted" stroke="#8A9A93" strokeDasharray="6 4" strokeWidth={2} dot={false} isAnimationActive={false} />
              </LineChart>
            </ResponsiveContainer>
            <p className="muted small">Forecast from asset age, traffic load and wear rate for this asset type. Solid line: recorded inspections. Dashed: projected condition.</p>
          </Panel>

          <Panel title="Inspection history">
            <ol className="timeline">
              {a.inspections.map((i) => (
                <li key={i.id}>
                  <span className="tl-dot" style={{ background: COND_COLOR[i.score < 25 ? "critical" : i.score < 50 ? "poor" : i.score < 75 ? "fair" : "good"] }} />
                  <div>
                    <strong>{dateIN(i.date)}: condition {fmt(i.score, 0)}</strong>
                    <span>{i.inspector}</span>
                    {i.defects && <span className="muted">Defects: {i.defects}</span>}
                    {i.remarks && <span className="muted">{i.remarks}</span>}
                  </div>
                </li>
              ))}
            </ol>
          </Panel>
        </div>

        <div className="col">
          <Panel className="flush">
            <MapView points={[a]} height={220} zoom={13} />
          </Panel>
          <Panel title="Asset record">
            <dl className="facts">
              <div><dt>Asset code</dt><dd>{a.code}</dd></div>
              <div><dt>Circle</dt><dd>{a.circle}</dd></div>
              <div><dt>Year built</dt><dd>{a.year_built} ({new Date().getFullYear() - a.year_built} years)</dd></div>
              {a.length_km && <div><dt>Length</dt><dd>{fmt(a.length_km, 1)} km</dd></div>}
              {a.span_m && <div><dt>Span</dt><dd>{fmt(a.span_m)} m</dd></div>}
              {a.floors && <div><dt>Floors</dt><dd>{a.floors}</dd></div>}
              {a.traffic_aadt > 0 && <div><dt>Traffic (AADT)</dt><dd>{fmt(a.traffic_aadt)} vehicles a day</dd></div>}
              <div><dt>Replacement value</dt><dd>{crore(a.value_cr)}</dd></div>
              <div><dt>Last inspected</dt><dd>{dateIN(a.last_inspection)}</dd></div>
              <div><dt>Next maintenance</dt><dd className={new Date(a.next_maintenance) <= new Date() ? "text-danger" : ""}>{dateIN(a.next_maintenance)}</dd></div>
            </dl>
          </Panel>

          {user.role !== "contractor" && (
            <Panel title="Log a field inspection">
              <form className="form" onSubmit={submit}>
                <label className="field">
                  <span>Condition index (0 to 100)</span>
                  <input type="number" min="0" max="100" step="1" required value={form.condition_score} onChange={(e) => setForm({ ...form, condition_score: e.target.value })} />
                  {preview && <small>This will mark the asset <CondPill condition={preview} /></small>}
                </label>
                <label className="field">
                  <span>Defects observed</span>
                  <input value={form.defects} onChange={(e) => setForm({ ...form, defects: e.target.value })} placeholder="e.g. potholes, edge breaking" />
                </label>
                <label className="field">
                  <span>Remarks</span>
                  <textarea rows={2} value={form.remarks} onChange={(e) => setForm({ ...form, remarks: e.target.value })} />
                </label>
                <label className="field">
                  <span>Operational status</span>
                  <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>
                    <option value="">Keep as {a.status}</option>
                    <option value="operational">Operational</option>
                    <option value="restricted">Restricted (load or traffic limit)</option>
                    <option value="closed">Closed</option>
                  </select>
                </label>
                {msg && <div className={msg.ok ? "ok-box" : "error-box"}>{msg.text}</div>}
                <button className="btn primary" disabled={busy}>{busy ? "Saving…" : "Save inspection"}</button>
              </form>
            </Panel>
          )}

          <Panel title="Works on this asset">
            {a.projects.length ? (
              <ul className="list">
                {a.projects.map((p) => (
                  <li key={p.id} className="stacked">
                    <Link to={`/projects/${p.id}`}><span className="list-main">{p.name}</span><span className="list-meta">{p.code}, {crore(p.cost_cr)}</span></Link>
                    <Progress value={p.progress} />
                  </li>
                ))}
              </ul>
            ) : <p className="muted">No works sanctioned on this asset yet.</p>}
          </Panel>
        </div>
      </div>
    </div>
  );
}
