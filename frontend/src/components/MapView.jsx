import { useEffect } from "react";
import { MapContainer, TileLayer, CircleMarker, Tooltip, useMap } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import { COND_COLOR, fmt, TYPE_ONE } from "../lib.jsx";

function Fit({ points, zoom }) {
  const map = useMap();
  const key = points.map((p) => `${p.lat.toFixed(3)},${p.lng.toFixed(3)}`).join("|");
  useEffect(() => {
    if (!points.length) return;
    if (points.length === 1) return void map.setView([points[0].lat, points[0].lng], zoom || 12);
    const lats = points.map((p) => p.lat), lngs = points.map((p) => p.lng);
    map.fitBounds([[Math.min(...lats), Math.min(...lngs)], [Math.max(...lats), Math.max(...lngs)]], { padding: [36, 36], maxZoom: 12 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);
  return null;
}

/**
 * bubbles: aggregated nodes [{id, name, lat, lng, value, critical, onClick}]
 * points: individual assets [{id, name, lat, lng, condition, type}]
 */
export default function MapView({ bubbles = [], points = [], onPoint, height = 380, zoom }) {
  const max = Math.max(1, ...bubbles.map((b) => b.value));
  const fitTo = bubbles.length ? bubbles : points;
  return (
    <div className="map-wrap" style={{ height }}>
      <MapContainer center={[22.6, 71.6]} zoom={7} preferCanvas scrollWheelZoom={false} style={{ height: "100%", width: "100%" }}>
        <TileLayer
          url="https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png"
          attribution='&copy; OpenStreetMap contributors &copy; CARTO'
        />
        <Fit points={fitTo} zoom={zoom} />
        {bubbles.map((b) => (
          <CircleMarker
            key={`b${b.id}`}
            center={[b.lat, b.lng]}
            radius={10 + Math.sqrt(b.value / max) * 26}
            pathOptions={{ color: "#0B6E4F", weight: 2, fillColor: b.critical > 0 ? "#0B6E4F" : "#3E8E73", fillOpacity: 0.28 }}
            eventHandlers={{ click: () => b.onClick?.() }}
          >
            <Tooltip direction="top">
              <strong>{b.name}</strong><br />
              {fmt(b.value)} assets{b.critical ? `, ${b.critical} critical` : ""}
            </Tooltip>
          </CircleMarker>
        ))}
        {points.map((p) => (
          <CircleMarker
            key={`p${p.id}`}
            center={[p.lat, p.lng]}
            radius={p.condition === "critical" ? 7 : 4.5}
            pathOptions={{ color: "#fff", weight: p.condition === "critical" ? 2 : 1, fillColor: COND_COLOR[p.condition], fillOpacity: 0.95 }}
            eventHandlers={{ click: () => onPoint?.(p) }}
          >
            <Tooltip direction="top">
              <strong>{p.name}</strong><br />
              {TYPE_ONE[p.type]}, {p.condition}
            </Tooltip>
          </CircleMarker>
        ))}
      </MapContainer>
    </div>
  );
}
