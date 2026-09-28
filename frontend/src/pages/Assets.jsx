import { useLocation } from "react-router-dom";
import { useApi } from "../lib.jsx";
import { Panel, Loading } from "../components/ui.jsx";
import AssetTable from "./AssetTable.jsx";

export default function Assets() {
  const { search } = useLocation();
  const params = Object.fromEntries(new URLSearchParams(search));
  const opts = useApi("/api/hierarchy/options");

  const heading = params.condition === "critical" ? "Critical assets" : params.due ? "Assets due for maintenance" : "Asset registry";
  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1 className="display">{heading}</h1>
          <p className="muted">Roads, bridges, culverts, buildings and government facilities in your jurisdiction. Select a row to see its inspections, forecast and works.</p>
        </div>
      </header>
      <Panel className="flush">
        {!opts.data ? <Loading /> : <AssetTable key={search} initial={params} scope={opts.data} />}
      </Panel>
    </div>
  );
}
