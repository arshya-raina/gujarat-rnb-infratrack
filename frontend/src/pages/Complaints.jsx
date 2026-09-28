import { useState } from "react";
import { Link } from "react-router-dom";
import { useApi, api, useAuth, fmt, ago, isOfficer, STATUS_LABEL } from "../lib.jsx";
import { Panel, Loading, ErrorBox, Pager, Status } from "../components/ui.jsx";

const FLOW = { open: ["assigned", "Assign to field team"], assigned: ["in_progress", "Start work"], in_progress: ["resolved", "Mark resolved"] };
const TABS = [["", "All"], ["open", "Open"], ["assigned", "Assigned"], ["in_progress", "In progress"], ["resolved", "Resolved"]];

export default function Complaints() {
  const { user } = useAuth();
  const officer = isOfficer(user);
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(1);
  const [notes, setNotes] = useState({});
  const res = useApi("/api/complaints", { status, page, page_size: 15 });
  const all = useApi("/api/complaints", { page_size: 1 });
  const counts = all.data?.counts || {};

  const move = async (c, next) => {
    await api(`/api/complaints/${c.id}`, { method: "PATCH", body: { status: next, resolution_note: notes[c.id] || (next === "resolved" ? "Attended by sub-division team" : "") } });
    res.reload();
    all.reload();
  };

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="display">{officer ? "Citizen complaints" : "My complaints"}</h1>
          <p className="muted">
            {officer
              ? "Complaints are routed automatically to the nearest sub-division. Move each one along as your team attends to it."
              : "Complaints you filed while signed in. Anyone can also track a complaint by ticket number on the public portal."}
          </p>
        </div>
        {!officer && <Link className="btn primary" to="/public">Report a new issue</Link>}
      </header>

      <div className="tabs" role="tablist">
        {TABS.map(([v, l]) => (
          <button key={v} role="tab" aria-selected={status === v} className={status === v ? "on" : ""} onClick={() => { setStatus(v); setPage(1); }}>
            {l}
            {v && counts[v] !== undefined && <b>{fmt(counts[v])}</b>}
          </button>
        ))}
      </div>

      <ErrorBox error={res.error} />
      {!res.data ? <Loading /> : (
        <div className="complaints">
          {res.data.items.map((c) => (
            <article key={c.id} className={`complaint prio-${c.priority}`}>
              <header>
                <div>
                  <strong>{c.category}</strong>
                  <span className="muted small">{c.ticket}, filed {ago(c.created_at)}</span>
                </div>
                <div className="pill-row">
                  <span className={`prio prio-tag-${c.priority}`}>{c.priority} priority</span>
                  <Status value={c.status} />
                </div>
              </header>
              <p>{c.description}</p>
              <p className="muted small">{c.location}. Routed to {c.subdivision} Sub-Division, {c.division}.</p>
              {c.resolution_note && <p className="ok-note">{c.resolution_note}</p>}
              {officer && FLOW[c.status] && (
                <footer>
                  <input placeholder="Note for the citizen (optional)" value={notes[c.id] || ""} onChange={(e) => setNotes({ ...notes, [c.id]: e.target.value })} />
                  <button className="btn primary sm" onClick={() => move(c, FLOW[c.status][0])}>{FLOW[c.status][1]}</button>
                </footer>
              )}
            </article>
          ))}
          {!res.data.items.length && (
            <p className="empty">{status ? `No ${STATUS_LABEL[status].toLowerCase()} complaints.` : officer ? "No complaints in your jurisdiction." : "You haven't filed a complaint yet."}</p>
          )}
          <Pager page={page} pageSize={15} total={res.data.total} onPage={setPage} />
        </div>
      )}
    </div>
  );
}
