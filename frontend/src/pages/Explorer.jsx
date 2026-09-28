import { useParams, useNavigate, Link, Navigate } from "react-router-dom";
import { useAuth, useApi, fmt, crore, LEVEL_LABEL, COND_COLOR } from "../lib.jsx";
import { Panel, Loading, ErrorBox, ConditionStack, Progress, Status } from "../components/ui.jsx";
import MapView from "../components/MapView.jsx";
import AssetTable from "./AssetTable.jsx";

export default function ExplorerRoute() {
  const { user } = useAuth();
  const { level, id } = useParams();
  if (!level && user.root.level !== "state") return <Navigate to={`/explore/${user.root.level}/${user.root.id}`} replace />;
  return <Explorer key={`${level || "state"}-${id || ""}`} level={level || "state"} id={id ? Number(id) : undefined} />;
}

function Explorer({ level, id }) {
  const nav = useNavigate();

  const node = useApi("/api/hierarchy/node", { level, id });
  const leaf = level === "subdivision";
  const filterKey = { region: "region_id", division: "division_id", subdivision: "subdivision_id" }[level];
  const geo = useApi(leaf ? "/api/assets/geo" : null, { subdivision_id: id });
  const works = useApi("/api/projects", { status: "in_progress", page_size: 6, ...(filterKey ? { [filterKey]: id } : {}) });

  if (node.error) return <div className="page"><ErrorBox error={node.error} /></div>;
  if (!node.data) return <div className="page"><Loading label="Loading jurisdiction" /></div>;
  const { node: n, breadcrumb, stats: s, children, child_level } = node.data;
  const maxAssets = Math.max(1, ...children.map((c) => c.asset_count));

  return (
    <div className="page">
      <nav className="crumbs" aria-label="Breadcrumb">
        {breadcrumb.map((c, i) => (
          <span key={i}>
            {i < breadcrumb.length - 1 ? (
              <Link to={c.level === "state" ? "/explore" : `/explore/${c.level}/${c.id}`}>{c.name}</Link>
            ) : (
              <b>{c.name}</b>
            )}
          </span>
        ))}
      </nav>

      <header className="page-head">
        <div>
          <p className="level-tag">{LEVEL_LABEL[level]}</p>
          <h1 className="display">{n.name}</h1>
          <p className="muted">{n.subtitle}</p>
        </div>
      </header>

      <div className="strip">
        <div><b>{fmt(s.total_assets)}</b><span>assets</span></div>
        <div><b>{fmt(s.active_projects)}</b><span>active works</span></div>
        <div className="warn"><b>{fmt(s.maintenance_due)}</b><span>maintenance due</span></div>
        <div className="danger"><b>{fmt(s.critical_assets)}</b><span>critical</span></div>
        <div><b>{fmt(s.avg_condition, 1)}</b><span>avg. condition</span></div>
        <div><b>{crore(s.active_budget_cr)}</b><span>active budget</span></div>
      </div>

      <div className="grid-board">
        {leaf ? (
          <Panel title="Condition mix">
            <ConditionStack counts={s.by_condition} total={s.total_assets} />
            <ul className="cond-legend">
              {Object.entries(COND_COLOR).map(([c, col]) => (
                <li key={c}><Link to={`/assets?subdivision_id=${id}&condition=${c}`}><i style={{ background: col }} /><span>{c}</span><b>{fmt(s.by_condition[c] || 0)}</b></Link></li>
              ))}
            </ul>
          </Panel>
        ) : (
          <Panel title={`${LEVEL_LABEL[child_level]}s in ${n.name}`} className="flush">
            <table className="table children">
              <thead>
                <tr><th>{LEVEL_LABEL[child_level]}</th><th className="num">Assets</th><th>Condition</th><th className="num">Due</th><th className="num">Critical</th><th className="num">Works</th></tr>
              </thead>
              <tbody>
                {children.map((c) => (
                  <tr key={c.id} className="clickable" onClick={() => nav(`/explore/${c.level}/${c.id}`)}>
                    <td>
                      <Link to={`/explore/${c.level}/${c.id}`} onClick={(e) => e.stopPropagation()}>{c.name}</Link>
                      <div className="mini-bar"><span style={{ width: `${(c.asset_count / maxAssets) * 100}%` }} /></div>
                    </td>
                    <td className="num">{fmt(c.asset_count)}</td>
                    <td><span className="score" style={{ color: c.avg_condition < 65 ? "#B25A12" : undefined }}>{fmt(c.avg_condition, 1)}</span></td>
                    <td className="num">{fmt(c.maintenance_due)}</td>
                    <td className={`num ${c.critical ? "text-danger" : ""}`}>{fmt(c.critical)}</td>
                    <td className="num">{fmt(c.active_projects)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Panel>
        )}
        <Panel title="Map" className="flush">
          <MapView
            height={leaf ? 360 : 380}
            bubbles={leaf ? [] : children.map((c) => ({ ...c, value: c.asset_count, onClick: () => nav(`/explore/${c.level}/${c.id}`) }))}
            points={leaf ? geo.data || [] : []}
            onPoint={(p) => nav(`/assets/${p.id}`)}
          />
        </Panel>
      </div>

      <Panel title={`Active works in ${n.name}`} action={<Link className="link" to={`/projects?status=in_progress${filterKey ? `&${filterKey}=${id}` : ""}`}>All {fmt(works.data?.total)} works</Link>} className="flush">
        {!works.data ? <Loading /> : (
          <table className="table">
            <thead><tr><th>Work</th><th>Scheme</th><th className="num">Cost</th><th>Progress</th><th>Status</th></tr></thead>
            <tbody>
              {works.data.items.map((p) => (
                <tr key={p.id} className="clickable" onClick={() => nav(`/projects/${p.id}`)}>
                  <td><Link to={`/projects/${p.id}`} onClick={(e) => e.stopPropagation()}>{p.name}</Link><div className="sub">{p.code}</div></td>
                  <td className="sub-cell">{p.scheme}</td>
                  <td className="num">{crore(p.cost_cr)}</td>
                  <td style={{ minWidth: 120 }}><Progress value={p.progress} tone={p.delayed ? "warn" : ""} /><span className="sub">{fmt(p.progress, 0)}%</span></td>
                  <td>{p.delayed ? <span className="status status-delayed">Behind schedule</span> : <Status value={p.status} />}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>

      {leaf && (
        <Panel title="Asset register for this sub-division" className="flush">
          <AssetTable fixed={{ subdivision_id: id }} />
        </Panel>
      )}
    </div>
  );
}
