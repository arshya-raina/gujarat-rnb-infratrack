import { useState } from "react";
import { fmt } from "../lib.jsx";

/**
 * A board styled like a Gujarat highway direction sign: white inset border,
 * place names on the left, figures on the right, like distances.
 * rows: [{ key, name, value, note, onClick, group }]  rows with group="others" collapse into one line.
 */
export default function SignBoard({ title, rows, unit = "assets" }) {
  const [expanded, setExpanded] = useState(false);
  const main = rows.filter((r) => r.group !== "others");
  const others = rows.filter((r) => r.group === "others");
  const othersTotal = others.reduce((s, r) => s + r.value, 0);

  const Row = ({ r, sub }) => (
    <li>
      <button className={`sign-row ${sub ? "sub" : ""}`} onClick={r.onClick} disabled={!r.onClick}>
        <span className="sign-place">{r.name}</span>
        <span className="sign-leader" aria-hidden />
        <span className="sign-fig">{fmt(r.value)}</span>
        {r.note ? <span className="sign-note">{r.note}</span> : <span className="sign-note" />}
      </button>
    </li>
  );

  return (
    <div className="sign">
      <div className="sign-inner">
        <div className="sign-title">
          <span>{title}</span>
          <span className="sign-unit">{unit}</span>
        </div>
        <ul>
          {main.map((r) => <Row key={r.key} r={r} />)}
          {others.length > 0 && (
            <>
              <li>
                <button className="sign-row" onClick={() => setExpanded(!expanded)} aria-expanded={expanded}>
                  <span className="sign-place">Others <small>{expanded ? "hide circles" : `${others.length} circles`}</small></span>
                  <span className="sign-leader" aria-hidden />
                  <span className="sign-fig">{fmt(othersTotal)}</span>
                  <span className="sign-note" />
                </button>
              </li>
              {expanded && others.map((r) => <Row key={r.key} r={r} sub />)}
            </>
          )}
        </ul>
      </div>
    </div>
  );
}
