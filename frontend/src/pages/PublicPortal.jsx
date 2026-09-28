import { useState } from "react";
import { MapContainer, TileLayer, CircleMarker, useMapEvents } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import { useApi, api, useAuth, fmt, crore, dateIN } from "../lib.jsx";
import { Panel, Loading, ErrorBox, Progress, Status } from "../components/ui.jsx";

function Picker({ value, onPick }) {
  useMapEvents({ click: (e) => onPick({ lat: +e.latlng.lat.toFixed(5), lng: +e.latlng.lng.toFixed(5) }) });
  return value ? <CircleMarker center={[value.lat, value.lng]} radius={9} pathOptions={{ color: "#fff", weight: 3, fillColor: "#C62F2A", fillOpacity: 1 }} /> : null;
}

export default function PublicPortal() {
  const { user } = useAuth();
  const stats = useApi("/api/public/stats");
  const cats = useApi("/api/complaints/categories");
  const works = useApi("/api/public/works");
  const [form, setForm] = useState({ category: "", description: "", location_text: "", citizen_name: user?.role === "citizen" ? user.name : "", phone: "" });
  const [pt, setPt] = useState(null);
  const [filed, setFiled] = useState(null);
  const [err, setErr] = useState("");
  const [ticket, setTicket] = useState("");
  const [tracked, setTracked] = useState(null);
  const [tErr, setTErr] = useState("");

  const locate = () =>
    navigator.geolocation?.getCurrentPosition(
      (p) => setPt({ lat: +p.coords.latitude.toFixed(5), lng: +p.coords.longitude.toFixed(5) }),
      () => setErr("Location access was blocked. Tap the map to mark the spot instead.")
    );

  const submit = async (e) => {
    e.preventDefault();
    setErr("");
    if (!pt) return setErr("Tap the map to mark where the problem is.");
    try {
      const out = await api("/api/complaints", { method: "POST", body: { ...form, ...pt } });
      setFiled(out);
      setTicket(out.ticket);
      setForm({ ...form, category: "", description: "", location_text: "" });
      setPt(null);
    } catch (x) {
      setErr(x.message);
    }
  };

  const track = async (e) => {
    e.preventDefault();
    setTErr("");
    setTracked(null);
    try {
      setTracked(await api(`/api/complaints/track/${encodeURIComponent(ticket)}`));
    } catch (x) {
      setTErr(x.message);
    }
  };

  const s = stats.data;
  return (
    <div className="page">
      <header className="public-hero">
        <h1 className="display">Report a damaged road, bridge or government building</h1>
        <p className="measure">
          Your report goes straight to the Roads &amp; Buildings sub-division responsible for that spot. You get a ticket number to follow it until it is fixed.
        </p>
      </header>

      {s && (
        <div className="strip public">
          <div><b>{fmt(s.total_assets)}</b><span>public assets maintained</span></div>
          <div><b>{fmt(s.road_km)} km</b><span>of roads</span></div>
          <div><b>{fmt(s.bridges)}</b><span>bridges</span></div>
          <div><b>{fmt(s.active_projects)}</b><span>works under way</span></div>
          <div><b>{s.complaints_resolved_pct}%</b><span>of complaints resolved</span></div>
        </div>
      )}

      <div className="grid-detail">
        <Panel title="Report an issue">
          {filed ? (
            <div className="filed">
              <p className="muted">Your complaint is registered.</p>
              <p className="ticket">{filed.ticket}</p>
              <p>{filed.routing}. Priority set to {filed.priority}.</p>
              <p className="muted small">Save this ticket number to track progress.</p>
              <button className="btn ghost" onClick={() => setFiled(null)}>Report another issue</button>
            </div>
          ) : (
            <form className="form" onSubmit={submit}>
              <label className="field">
                <span>What is the problem?</span>
                <select required value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
                  <option value="">Choose one</option>
                  {(cats.data || []).map((c) => <option key={c}>{c}</option>)}
                </select>
              </label>
              <div className="field">
                <span>Where is it? Tap the map to drop a pin.</span>
                <div className="map-wrap picker">
                  <MapContainer center={[22.6, 71.6]} zoom={7} style={{ height: "100%" }}>
                    <TileLayer url="https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png" attribution="&copy; OpenStreetMap contributors &copy; CARTO" />
                    <Picker value={pt} onPick={setPt} />
                  </MapContainer>
                </div>
                <div className="row-between">
                  <small className="muted">{pt ? `Pin at ${pt.lat}, ${pt.lng}` : "No pin yet"}</small>
                  <button type="button" className="btn ghost sm" onClick={locate}>Use my location</button>
                </div>
              </div>
              <label className="field">
                <span>Landmark or address</span>
                <input required value={form.location_text} onChange={(e) => setForm({ ...form, location_text: e.target.value })} placeholder="e.g. Near Athwa Gate circle, towards Majura" />
              </label>
              <label className="field">
                <span>Describe what you saw</span>
                <textarea required minLength={5} rows={3} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
              </label>
              <div className="two">
                <label className="field">
                  <span>Your name</span>
                  <input required value={form.citizen_name} onChange={(e) => setForm({ ...form, citizen_name: e.target.value })} />
                </label>
                <label className="field">
                  <span>Mobile (optional)</span>
                  <input inputMode="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
                </label>
              </div>
              {err && <div className="error-box">{err}</div>}
              <button className="btn primary">Submit complaint</button>
            </form>
          )}
        </Panel>

        <div className="col">
          <Panel title="Track a complaint">
            <form className="inline-form" onSubmit={track}>
              <input value={ticket} onChange={(e) => setTicket(e.target.value)} placeholder="Ticket number, e.g. GRB-2026-000123" required />
              <button className="btn primary">Track</button>
            </form>
            {tErr && <div className="error-box">{tErr}</div>}
            {tracked && (
              <div className="tracked">
                <div className="row-between"><strong>{tracked.category}</strong><Status value={tracked.status} /></div>
                <ol className="steps">
                  {["open", "assigned", "in_progress", "resolved"].map((st, i, arr) => (
                    <li key={st} className={arr.indexOf(tracked.status) >= i ? "done" : ""}>{{ open: "Received", assigned: "Assigned", in_progress: "Work started", resolved: "Resolved" }[st]}</li>
                  ))}
                </ol>
                <p className="muted small">{tracked.location}. Handled by {tracked.subdivision} Sub-Division, {tracked.region} circle. Last update {dateIN(tracked.updated_at)}.</p>
                {tracked.resolution_note && <p className="ok-note">{tracked.resolution_note}</p>}
              </div>
            )}
          </Panel>

          <Panel title="Assets by circle">
            {!s ? <Loading /> : (
              <ul className="bars">
                {s.regions.map((r) => (
                  <li key={r.id}>
                    <span>{r.name}</span>
                    <div className="mini-bar"><span style={{ width: `${(r.assets / Math.max(...s.regions.map((x) => x.assets))) * 100}%` }} /></div>
                    <b>{fmt(r.assets)}</b>
                  </li>
                ))}
              </ul>
            )}
          </Panel>
        </div>
      </div>

      <Panel title="Major works under way" className="flush">
        <ErrorBox error={works.error} />
        {!works.data ? <Loading /> : (
          <div className="table-scroll">
            <table className="table">
              <thead><tr><th>Work</th><th>Circle</th><th className="num">Cost</th><th>Progress</th><th>Target</th></tr></thead>
              <tbody>
                {works.data.map((w) => (
                  <tr key={w.code}>
                    <td>{w.name}<div className="sub">{w.code}</div></td>
                    <td>{w.region}</td>
                    <td className="num">{crore(w.cost_cr)}</td>
                    <td style={{ minWidth: 120 }}><Progress value={w.progress} /><span className="sub">{fmt(w.progress, 0)}%</span></td>
                    <td>{dateIN(w.target_date)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    </div>
  );
}
