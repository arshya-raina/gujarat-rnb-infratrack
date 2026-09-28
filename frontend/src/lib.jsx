import { createContext, useContext, useEffect, useState, useCallback } from "react";

// ------------------------------------------------------------------ API client
const TOKEN_KEY = "infratrack_token";

export async function api(path, { method = "GET", body, params } = {}) {
  let url = path;
  if (params) {
    const qs = new URLSearchParams(
      Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== "" && v !== false)
    ).toString();
    if (qs) url += `?${qs}`;
  }
  const headers = { "Content-Type": "application/json" };
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(url, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const msg = typeof data.detail === "string" ? data.detail : Array.isArray(data.detail) ? data.detail.map((d) => d.msg).join(", ") : "Request failed";
    const err = new Error(msg);
    err.status = res.status;
    throw err;
  }
  return data;
}

/** Fetch hook: re-runs when deps change. Returns { data, error, loading, reload, setData }. */
export function useApi(path, params, deps = []) {
  const [state, setState] = useState({ data: null, error: null, loading: true });
  const key = JSON.stringify(params || {});
  const load = useCallback(() => {
    if (!path) return;
    setState((s) => ({ ...s, loading: true, error: null }));
    api(path, { params })
      .then((data) => setState({ data, error: null, loading: false }))
      .catch((error) => setState({ data: null, error, loading: false }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [path, key, ...deps]);
  useEffect(load, [load]);
  return { ...state, reload: load, setData: (data) => setState((s) => ({ ...s, data })) };
}

// ------------------------------------------------------------------ auth
const AuthCtx = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!localStorage.getItem(TOKEN_KEY)) return setReady(true);
    api("/api/auth/me")
      .then(setUser)
      .catch(() => localStorage.removeItem(TOKEN_KEY))
      .finally(() => setReady(true));
  }, []);

  const login = async (email, password) => {
    const res = await api("/api/auth/login", { method: "POST", body: { email, password } });
    localStorage.setItem(TOKEN_KEY, res.token);
    setUser(res.user);
    return res.user;
  };
  const logout = () => {
    localStorage.removeItem(TOKEN_KEY);
    setUser(null);
  };
  return <AuthCtx.Provider value={{ user, ready, login, logout }}>{children}</AuthCtx.Provider>;
}

export const useAuth = () => useContext(AuthCtx);
export const isOfficer = (u) => u && ["state_admin", "regional_officer", "division_engineer", "subdivision_engineer"].includes(u.role);
export const homeFor = (u) => (!u ? "/login" : isOfficer(u) ? "/" : u.role === "contractor" ? "/projects" : "/public");

// ------------------------------------------------------------------ formatting
export const fmt = (n, d = 0) =>
  n === null || n === undefined ? "–" : Number(n).toLocaleString("en-IN", { maximumFractionDigits: d, minimumFractionDigits: d });
export const crore = (n) => `₹${fmt(n, n >= 100 ? 0 : 1)} Cr`;
export const dateIN = (s) => (s ? new Date(s).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) : "–");
export const ago = (s) => {
  const m = Math.round((Date.now() - new Date(s).getTime()) / 60000);
  if (m < 60) return `${m} min ago`;
  if (m < 60 * 24) return `${Math.round(m / 60)} h ago`;
  return `${Math.round(m / 1440)} d ago`;
};

export const TYPE_LABEL = { road: "Roads", bridge: "Bridges", building: "Buildings", culvert: "Culverts", facility: "Govt. facilities", other: "Other assets" };
export const TYPE_ONE = { road: "Road", bridge: "Bridge", building: "Building", culvert: "Culvert", facility: "Govt. facility", other: "Other" };
export const TYPES = ["road", "bridge", "building", "culvert", "facility", "other"];
export const COND_COLOR = { good: "#2F8F5B", fair: "#D39B12", poor: "#E0701E", critical: "#C62F2A" };
export const CONDITIONS = ["good", "fair", "poor", "critical"];
export const STATUS_LABEL = {
  in_progress: "In progress", completed: "Completed", sanctioned: "Sanctioned", tendering: "Tendering", on_hold: "On hold",
  open: "Open", assigned: "Assigned", resolved: "Resolved", submitted: "Submitted", verified: "Verified", approved: "Approved",
  paid: "Paid", rejected: "Rejected", operational: "Operational", restricted: "Restricted", closed: "Closed",
};
export const LEVEL_LABEL = { state: "State", region: "Circle", division: "Division", subdivision: "Sub-Division" };
