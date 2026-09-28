import { useState } from "react";
import { useParams, Link } from "react-router-dom";
import { useApi, api, useAuth, fmt, crore, dateIN, STATUS_LABEL } from "../lib.jsx";
import { Panel, Loading, ErrorBox, Progress, Status, CondPill } from "../components/ui.jsx";

const APPROVERS = ["division_engineer", "regional_officer", "state_admin"];
const NEXT = { submitted: ["verified", "Verify"], verified: ["approved", "Approve"], approved: ["paid", "Mark paid"] };

export default function ProjectDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const res = useApi(`/api/projects/${id}`);
  const [prog, setProg] = useState({ physical_progress: "", remarks: "" });
  const [bill, setBill] = useState({ amount_lakh: "", remarks: "" });
  const [msg, setMsg] = useState(null);

  if (res.error) return <div className="page"><ErrorBox error={res.error} /></div>;
  if (!res.data) return <div className="page"><Loading label="Loading work" /></div>;
  const p = res.data;
  const isContractor = user.role === "contractor";
  const canReport = (isContractor || user.role === "subdivision_engineer" || user.role === "division_engineer") && ["in_progress", "on_hold"].includes(p.status);

  const act = async (fn, okText) => {
    setMsg(null);
    try {
      res.setData(await fn());
      setMsg({ ok: true, text: okText });
    } catch (x) {
      setMsg({ ok: false, text: x.message });
    }
  };

  return (
    <div className="page">
      <nav className="crumbs">
        <span><Link to="/projects">{isContractor ? "My awarded works" : "Projects & works"}</Link></span>
        <span><b>{p.code}</b></span>
      </nav>
      <header className="page-head">
        <div>
          <p className="level-tag">{p.scheme}</p>
          <h1 className="display">{p.name}</h1>
          <div className="pill-row">
            <Status value={p.status} />
            {p.delayed && <span className="status status-delayed">Behind schedule</span>}
            <span className="muted">{p.subdivision} Sub-Division, {p.division}</span>
          </div>
        </div>
      </header>

      <div className="strip">
        <div><b>{crore(p.cost_cr)}</b><span>sanctioned</span></div>
        <div><b>{crore(p.spent_cr)}</b><span>spent</span></div>
        <div><b>{fmt(p.progress, 1)}%</b><span>physical progress</span></div>
        <div className={p.days_left < 0 && p.status !== "completed" ? "danger" : ""}><b>{p.status === "completed" ? "Done" : p.days_left >= 0 ? `${p.days_left} days` : `${-p.days_left} days late`}</b><span>{p.status === "completed" ? "completed" : "to target date"}</span></div>
      </div>

      {msg && <div className={msg.ok ? "ok-box" : "error-box"}>{msg.text}</div>}

      <div className="grid-detail">
        <div className="col">
          <Panel title="Progress against plan">
            <Progress value={p.progress} expected={p.expected_progress} tone={p.delayed ? "warn" : ""} />
            <p className="muted small gap">
              {p.expected_progress !== null
                ? `Actual ${fmt(p.progress, 1)}% against ${fmt(p.expected_progress, 1)}% planned by today (marker). Started ${dateIN(p.start_date)}, due ${dateIN(p.target_date)}.`
                : `Start ${dateIN(p.start_date)}, target ${dateIN(p.target_date)}.`}
            </p>
            <ol className="timeline">
              {p.updates.map((u) => (
                <li key={u.id}>
                  <span className="tl-dot" />
                  <div>
                    <strong>{dateIN(u.date)}: {fmt(u.progress, 1)}% complete</strong>
                    <span>{u.remarks}</span>
                    <span className="muted">{u.by}</span>
                  </div>
                </li>
              ))}
              {!p.updates.length && <li className="muted">No progress reported yet.</li>}
            </ol>
          </Panel>

          <Panel title="Running account bills" className="flush">
            <table className="table">
              <thead><tr><th>Bill</th><th className="num">Amount</th><th>Submitted</th><th>Status</th>{APPROVERS.includes(user.role) && <th />}</tr></thead>
              <tbody>
                {p.bills.map((b) => (
                  <tr key={b.id}>
                    <td>{b.bill_no}{b.remarks && <div className="sub">{b.remarks}</div>}</td>
                    <td className="num">₹{fmt(b.amount_lakh, 2)} lakh</td>
                    <td>{dateIN(b.submitted_on)}</td>
                    <td><Status value={b.status} /></td>
                    {APPROVERS.includes(user.role) && (
                      <td className="actions">
                        {NEXT[b.status] && (
                          <button className="btn primary sm" onClick={() => act(() => api(`/api/projects/bills/${b.id}`, { method: "PATCH", body: { status: NEXT[b.status][0] } }), `${b.bill_no} ${STATUS_LABEL[NEXT[b.status][0]].toLowerCase()}.`)}>
                            {NEXT[b.status][1]}
                          </button>
                        )}
                        {["submitted", "verified"].includes(b.status) && (
                          <button className="btn ghost sm" onClick={() => act(() => api(`/api/projects/bills/${b.id}`, { method: "PATCH", body: { status: "rejected", remarks: "Returned for correction" } }), `${b.bill_no} returned to contractor.`)}>
                            Return
                          </button>
                        )}
                      </td>
                    )}
                  </tr>
                ))}
                {!p.bills.length && <tr><td colSpan={5} className="empty">No bills submitted on this work.</td></tr>}
              </tbody>
            </table>
          </Panel>
        </div>

        <div className="col">
          {canReport && (
            <Panel title="Report progress">
              <form className="form" onSubmit={(e) => { e.preventDefault(); act(() => api(`/api/projects/${id}/progress`, { method: "POST", body: { physical_progress: Number(prog.physical_progress), remarks: prog.remarks } }).then((r) => { setProg({ physical_progress: "", remarks: "" }); return r; }), "Progress update recorded."); }}>
                <label className="field">
                  <span>Physical progress (%)</span>
                  <input type="number" min={p.progress} max="100" step="0.5" required value={prog.physical_progress} onChange={(e) => setProg({ ...prog, physical_progress: e.target.value })} placeholder={`At least ${p.progress}`} />
                </label>
                <label className="field">
                  <span>Work done since last update</span>
                  <textarea rows={2} required value={prog.remarks} onChange={(e) => setProg({ ...prog, remarks: e.target.value })} placeholder="e.g. Bituminous layer laid from km 4 to km 7" />
                </label>
                <button className="btn primary">Submit progress</button>
              </form>
            </Panel>
          )}
          {isContractor && p.status === "in_progress" && (
            <Panel title="Submit a running account bill">
              <form className="form" onSubmit={(e) => { e.preventDefault(); act(() => api(`/api/projects/${id}/bills`, { method: "POST", body: { amount_lakh: Number(bill.amount_lakh), remarks: bill.remarks } }).then((r) => { setBill({ amount_lakh: "", remarks: "" }); return r; }), "Bill submitted to the division for verification."); }}>
                <label className="field">
                  <span>Amount (₹ lakh)</span>
                  <input type="number" min="0.01" step="0.01" required value={bill.amount_lakh} onChange={(e) => setBill({ ...bill, amount_lakh: e.target.value })} />
                </label>
                <label className="field">
                  <span>Measurement book reference</span>
                  <input value={bill.remarks} onChange={(e) => setBill({ ...bill, remarks: e.target.value })} placeholder="e.g. MB No. 142, pages 18 to 24" />
                </label>
                <button className="btn primary">Submit bill</button>
              </form>
            </Panel>
          )}
          <Panel title="Work details">
            <dl className="facts">
              <div><dt>Work code</dt><dd>{p.code}</dd></div>
              <div><dt>Type of work</dt><dd>{p.work_type.replace("_", " ")}</dd></div>
              <div><dt>Circle</dt><dd>{p.region}</dd></div>
              <div><dt>Division</dt><dd>{p.division}</dd></div>
              <div><dt>Sub-division</dt><dd>{p.subdivision}</dd></div>
              <div><dt>Scheme</dt><dd>{p.scheme}</dd></div>
            </dl>
          </Panel>
          {p.contractor_detail && (
            <Panel title="Contractor">
              <dl className="facts">
                <div><dt>Firm</dt><dd>{p.contractor_detail.name}</dd></div>
                <div><dt>Registration</dt><dd>{p.contractor_detail.registration_no}</dd></div>
                <div><dt>Class</dt><dd>{p.contractor_detail.class}</dd></div>
                <div><dt>Performance rating</dt><dd>{p.contractor_detail.rating} out of 5</dd></div>
              </dl>
            </Panel>
          )}
          {p.asset && (
            <Panel title="Asset under this work">
              {isContractor ? (
                <p><strong>{p.asset.name}</strong><br /><span className="muted">{p.asset.code}</span></p>
              ) : (
                <Link to={`/assets/${p.asset.id}`} className="asset-link">
                  <strong>{p.asset.name}</strong>
                  <span className="muted">{p.asset.code}</span>
                  <CondPill condition={p.asset.condition} score={p.asset.score} />
                </Link>
              )}
            </Panel>
          )}
        </div>
      </div>
    </div>
  );
}
