import { useState } from "react";
import { NavLink, Outlet, useNavigate, useLocation } from "react-router-dom";
import { useAuth, isOfficer } from "../lib.jsx";

const OFFICER_NAV = [
  ["/", "Command center"],
  ["/explore", "Jurisdiction explorer"],
  ["/assets", "Asset registry"],
  ["/projects", "Projects & works"],
  ["/priority", "Maintenance priority"],
  ["/complaints", "Citizen complaints"],
  ["/public", "Public portal"],
];
const CONTRACTOR_NAV = [["/projects", "My awarded works"], ["/public", "Public portal"]];
const CITIZEN_NAV = [["/public", "Report & track"], ["/complaints", "My complaints"]];

export default function Layout() {
  const { user, logout } = useAuth();
  const nav = useNavigate();
  const loc = useLocation();
  const [open, setOpen] = useState(false);
  const items = !user ? [["/public", "Report & track"]] : isOfficer(user) ? OFFICER_NAV : user.role === "contractor" ? CONTRACTOR_NAV : CITIZEN_NAV;

  return (
    <div className="shell">
      <aside className={`sidebar ${open ? "open" : ""}`}>
        <div className="brand">
          <div className="brand-sign">
            <span className="brand-name">InfraTrack</span>
            <span className="brand-sub">Roads &amp; Buildings Department</span>
            <span className="brand-sub">Government of Gujarat</span>
          </div>
          <button className="menu-btn" aria-label="Toggle menu" onClick={() => setOpen(!open)}>{open ? "Close" : "Menu"}</button>
        </div>
        <nav className="nav" onClick={() => setOpen(false)}>
          {items.map(([to, label]) => (
            <NavLink key={to} to={to} end={to === "/"} className={({ isActive }) => (isActive || (to === "/explore" && loc.pathname.startsWith("/explore")) ? "active" : "")}>
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="me">
          {user ? (
            <>
              <strong>{user.name}</strong>
              <span>{user.designation}</span>
              {user.jurisdiction && <span className="me-scope">Jurisdiction: {user.jurisdiction}</span>}
              <button className="btn on-dark sm" onClick={() => { logout(); nav("/login"); }}>Sign out</button>
            </>
          ) : (
            <button className="btn on-dark sm" onClick={() => nav("/login")}>Sign in</button>
          )}
        </div>
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
