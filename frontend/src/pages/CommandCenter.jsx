import { useNavigate, Link } from "react-router-dom";
import { useAuth, useApi, fmt, crore, ago, TYPE_LABEL, TYPES, COND_COLOR, CONDITIONS, LEVEL_LABEL } from "../lib.jsx";
import { Kpi, Panel, ConditionStack, Loading, ErrorBox, CondPill, Progress } from "../components/ui.jsx";
import SignBoard from "../components/SignBoard.jsx";
import MapView from "../components/MapView.jsx";

export default function CommandCenter() {
  const { user } = useAuth();
  const nav = useNavigate();
  const { level, id } = user.root;
  const node = useApi("/api/hierarchy/node", { level, id });
  const alerts = useApi("/api/dashboard/alerts");
  const crit = useApi("/api/dashboard/critical-map");
  const activity = useApi("/api/activity", { limit: 12 });
  const leaf = level === "subdivision";
  const points = useApi(leaf ? "/api/assets/geo" : null, { subdivision_id: id });

  if (node.error) return <ErrorBox error={node.error} />;
  if (!node.data) return <Loading label="Opening command center" />;
  const { stats: s, children, child_level } = node.data;
  const today = new Date().toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "long", year: "numeric" });
  const title = level === "state" ? "Gujarat R&B Command Center" : `${node.data.node.name} command center`;

  const rows = children.map((c) => ({
    key: c.id,
    name: c.name,
    value: c.asset_count,
    note: c.critical ? `${c.critical} critical` : "",
    group: child_level === "region" && !c.is_focus ? "others" : undefined,
    onClick: () => nav(`/explore/${c.level}/${c.id}`),
  }));
  const typeData = TYPES.map((t) => ({ t, name: TYPE_LABEL[t], value: s.by_type[t] || 0 }));
  const ps = s.projects_by_status;

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="display">{title}</h1>
          <p className="muted">{today}. Showing {user.jurisdiction || node.data.node.name}.</p>
        </div>
        <div className="head-actions">
          <Link className="btn ghost" to="/priority">Maintenance priority list</Link>
          <Link className="btn primary" to={`/explore${level === "state" ? "" : `/${level}/${id}`}`}>Explore {LEVEL_LABEL[child_level || level]?.toLowerCase()}s</Link>
        </div>
      </header>

      <div className="kpis">
        <Kpi label="Total assets" value={s.total_assets} note={`${crore(s.asset_value_cr)} replacement value`} to="/assets" />
        <Kpi label="Active projects" value={s.active_projects} note={`${fmt(s.delayed_projects)} behind schedule`} to="/projects?status=in_progress" />
        <Kpi label="Maintenance due" value={s.maintenance_due} note="Due date today or passed" tone="warn" to="/assets?due=1" />
        <Kpi label="Critical assets" value={s.critical_assets} note="Condition index below 25" tone="danger" to="/assets?condition=critical" />
      </div>

      <div className="grid-board">
        {leaf ? (
          <Panel title="Condition of assets in this sub-division">
            <CondBreakdown s={s} />
          </Panel>
        ) : (
          <SignBoard title={child_level === "region" ? "Assets by circle" : `Assets by ${LEVEL_LABEL[child_level].toLowerCase()}`} rows={rows} />
        )}
        <Panel
          title={leaf ? "Asset map" : "Where the assets are"}
          action={<span className="legend"><i className="dot crit" /> Critical asset</span>}
          className="flush"
        >
          <MapView
            height={leaf ? 420 : 400}
            bubbles={leaf ? [] : children.map((c) => ({ ...c, value: c.asset_count, onClick: () => nav(`/explore/${c.level}/${c.id}`) }))}
            points={leaf ? points.data || [] : (crit.data || []).map((p) => ({ ...p, type: p.asset_type, condition: "critical" }))}
            onPoint={(p) => nav(`/assets/${p.id}`)}
          />
        </Panel>
      </div>

      <div className="grid-3">
        {!leaf && (
          <Panel title="Condition index">
            <CondBreakdown s={s} />
          </Panel>
        )}
        <Panel title="Asset types">
          <ul className="bars types">
            {typeData.map((d) => (
              <li key={d.t}>
                <Link to={`/assets?asset_type=${d.t}`}>{d.name}</Link>
                <div className="mini-bar"><span style={{ width: `${(d.value / Math.max(1, ...typeData.map((x) => x.value))) * 100}%` }} /></div>
                <b>{fmt(d.value)}</b>
              </li>
            ))}
          </ul>
        </Panel>
        <Panel title="Works pipeline">
          <ul className="pipeline">
            {[["sanctioned", "Sanctioned"], ["tendering", "Tendering"], ["in_progress", "In progress"], ["on_hold", "On hold"], ["completed", "Completed"]].map(([k, l]) => (
              <li key={k}>
                <Link to={`/projects?status=${k}`}>
                  <span>{l}</span>
                  <b>{fmt(ps[k]?.count || 0)}</b>
                </Link>
              </li>
            ))}
          </ul>
          <div className="budget">
            <div className="budget-line"><span>Active works budget</span><b>{crore(s.active_budget_cr)}</b></div>
            <Progress value={(s.active_spent_cr / (s.active_budget_cr || 1)) * 100} />
            <p className="muted small">{crore(s.active_spent_cr)} spent so far</p>
          </div>
        </Panel>
      </div>

      <div className="grid-3">
        <Panel title="Critical assets" action={<Link className="link" to="/assets?condition=critical">View all</Link>}>
          {!alerts.data ? <Loading /> : (
            <ul className="list">
              {alerts.data.critical_assets.map((a) => (
                <li key={a.id}>
                  <Link to={`/assets/${a.id}`}>
                    <span className="list-main">{a.name}</span>
                    <span className="list-meta">{a.code}, risk {a.risk}</span>
                  </Link>
                  <CondPill condition="critical" score={a.score} />
                </li>
              ))}
              {!alerts.data.critical_assets.length && <li className="muted">No critical assets in your jurisdiction.</li>}
            </ul>
          )}
        </Panel>
        <Panel title="Works behind schedule" action={<Link className="link" to="/projects?delayed=1">View all</Link>}>
          {!alerts.data ? <Loading /> : (
            <ul className="list">
              {alerts.data.delayed_projects.map((p) => (
                <li key={p.id} className="stacked">
                  <Link to={`/projects/${p.id}`}>
                    <span className="list-main">{p.name}</span>
                    <span className="list-meta">{crore(p.cost_cr)}, {p.days_left >= 0 ? `${p.days_left} days to deadline` : `${-p.days_left} days past deadline`}</span>
                  </Link>
                  <Progress value={p.progress} tone="warn" />
                </li>
              ))}
              {!alerts.data.delayed_projects.length && <li className="muted">All active works are on schedule.</li>}
            </ul>
          )}
        </Panel>
        <Panel title="Recent activity">
          {!activity.data ? <Loading /> : (
            <ul className="feed">
              {activity.data.map((a, i) => (
                <li key={i}>
                  <span className="feed-time">{ago(a.ts)}</span>
                  <strong>{a.action}</strong>
                  <span>{a.detail}</span>
                  <small>{a.user}</small>
                </li>
              ))}
            </ul>
          )}
        </Panel>
      </div>

      {alerts.data?.urgent_complaints?.length > 0 && (
        <Panel title="High-priority citizen complaints" action={<Link className="link" to="/complaints">Open complaints desk</Link>}>
          <div className="chips">
            {alerts.data.urgent_complaints.map((c) => (
              <Link key={c.id} to="/complaints" className="chip">
                <strong>{c.category}</strong>
                <span>{c.location}</span>
                <small>{c.ticket}, {ago(c.created_at)}</small>
              </Link>
            ))}
          </div>
        </Panel>
      )}
    </div>
  );
}

function CondBreakdown({ s }) {
  return (
    <div className="cond-break">
      <div className="cond-avg">
        <span className="big-num">{fmt(s.avg_condition, 1)}</span>
        <span className="muted">average condition index out of 100</span>
      </div>
      <ConditionStack counts={s.by_condition} total={s.total_assets} />
      <ul className="cond-legend">
        {CONDITIONS.map((c) => (
          <li key={c}>
            <Link to={`/assets?condition=${c}`}>
              <i style={{ background: COND_COLOR[c] }} />
              <span>{c}</span>
              <b>{fmt(s.by_condition[c] || 0)}</b>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
