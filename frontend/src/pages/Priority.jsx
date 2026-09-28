import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useApi, fmt, TYPE_ONE, TYPES } from "../lib.jsx";
import { Panel, Loading, ErrorBox, CondPill, Select } from "../components/ui.jsx";
import { RiskMeter } from "./AssetTable.jsx";

export default function Priority() {
  const nav = useNavigate();
  const [type, setType] = useState("");
  const [limit, setLimit] = useState(40);
  const res = useApi("/api/analytics/priority", { asset_type: type, limit });

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="display">Maintenance priority</h1>
          <p className="muted measure">
            Assets ranked by a risk index that weighs current condition, age, traffic load, time since the last inspection and how critical the
            asset type is. Use it to decide where this year's maintenance budget goes first.
          </p>
        </div>
      </header>

      <ErrorBox error={res.error} />
      {res.data && (
        <div className="bands">
          <div className="band band-high"><b>{fmt(res.data.bands.high)}</b><span>high risk, index 60 and above</span></div>
          <div className="band band-mid"><b>{fmt(res.data.bands.elevated)}</b><span>elevated, 40 to 59</span></div>
          <div className="band band-low"><b>{fmt(res.data.bands.low)}</b><span>low, below 40</span></div>
          <div className="band"><b>₹{fmt(res.data.total_cost_lakh / 100, 1)} Cr</b><span>to treat the {res.data.items.length} assets listed</span></div>
        </div>
      )}

      <Panel className="flush">
        <div className="filters">
          <Select label="Asset type" value={type} onChange={setType} options={TYPES.map((t) => ({ value: t, label: TYPE_ONE[t] }))} />
          <Select label="Show" value={limit} onChange={(v) => setLimit(Number(v))} all={null} options={[20, 40, 100, 200].map((n) => ({ value: n, label: `Top ${n}` }))} />
        </div>
        {!res.data ? <Loading label="Ranking assets" /> : (
          <div className="table-scroll">
            <table className="table">
              <thead>
                <tr><th className="num">Rank</th><th>Asset</th><th className="num">Risk</th><th>Condition</th><th>Reaches critical</th><th>Recommended action</th><th className="num">Est. cost</th></tr>
              </thead>
              <tbody>
                {res.data.items.map((a, i) => (
                  <tr key={a.id} className="clickable" onClick={() => nav(`/assets/${a.id}`)}>
                    <td className="num rank">{i + 1}</td>
                    <td><Link to={`/assets/${a.id}`} onClick={(e) => e.stopPropagation()}>{a.name}</Link><div className="sub">{a.code}, {a.subdivision}, built {a.year_built}</div></td>
                    <td className="num"><RiskMeter v={a.risk} /></td>
                    <td><CondPill condition={a.condition} score={a.score} /></td>
                    <td className={a.months_to_critical <= 12 ? "text-danger" : ""}>{a.months_to_critical === 0 ? "Now" : `${a.months_to_critical} months`}</td>
                    <td className="sub-cell">{a.action}</td>
                    <td className="num">₹{fmt(a.cost_lakh, 1)} L</td>
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
