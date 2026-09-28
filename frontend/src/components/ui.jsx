import { Link } from "react-router-dom";
import { COND_COLOR, CONDITIONS, STATUS_LABEL, fmt } from "../lib.jsx";

export function Kpi({ label, value, note, tone, to }) {
  const body = (
    <>
      <span className="kpi-value">{fmt(value)}</span>
      <span className="kpi-label">{label}</span>
      {note && <span className="kpi-note">{note}</span>}
    </>
  );
  const cls = `kpi ${tone ? `kpi-${tone}` : ""}`;
  return to ? <Link to={to} className={cls}>{body}</Link> : <div className={cls}>{body}</div>;
}

export function CondPill({ condition, score }) {
  return (
    <span className={`cond cond-${condition}`}>
      <i style={{ background: COND_COLOR[condition] }} />
      {condition}
      {score !== undefined && <b>{Math.round(score)}</b>}
    </span>
  );
}

export function Status({ value }) {
  return <span className={`status status-${value}`}>{STATUS_LABEL[value] || value}</span>;
}

export function Progress({ value, expected, tone }) {
  return (
    <div className="progress" title={`${fmt(value, 1)}% complete`}>
      <div className={`progress-fill ${tone || ""}`} style={{ width: `${Math.min(value, 100)}%` }} />
      {expected !== undefined && expected !== null && <div className="progress-expected" style={{ left: `${expected}%` }} />}
    </div>
  );
}

export function ConditionStack({ counts, total }) {
  const t = total || CONDITIONS.reduce((s, c) => s + (counts?.[c] || 0), 0) || 1;
  return (
    <div className="stack" aria-label="Condition mix">
      {CONDITIONS.map((c) => (counts?.[c] ? <span key={c} style={{ width: `${(counts[c] / t) * 100}%`, background: COND_COLOR[c] }} title={`${c}: ${fmt(counts[c])}`} /> : null))}
    </div>
  );
}

export function Loading({ label = "Loading" }) {
  return <div className="loading"><span className="dash-loader" />{label}…</div>;
}

export function ErrorBox({ error }) {
  if (!error) return null;
  return <div className="error-box">{error.message}</div>;
}

export function Pager({ page, pageSize, total, onPage }) {
  const pages = Math.max(1, Math.ceil(total / pageSize));
  return (
    <div className="pager">
      <span>
        {fmt((page - 1) * pageSize + 1)}–{fmt(Math.min(page * pageSize, total))} of {fmt(total)}
      </span>
      <button className="btn ghost sm" disabled={page <= 1} onClick={() => onPage(page - 1)}>Previous</button>
      <button className="btn ghost sm" disabled={page >= pages} onClick={() => onPage(page + 1)}>Next</button>
    </div>
  );
}

export function Panel({ title, action, children, className = "" }) {
  return (
    <section className={`panel ${className}`}>
      {(title || action) && (
        <header className="panel-head">
          <h3>{title}</h3>
          {action}
        </header>
      )}
      {children}
    </section>
  );
}

export function Select({ label, value, onChange, options, all = "All" }) {
  return (
    <label className="field">
      <span>{label}</span>
      <select value={value ?? ""} onChange={(e) => onChange(e.target.value)}>
        {all && <option value="">{all}</option>}
        {options.map((o) => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </select>
    </label>
  );
}
