import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth, useApi, homeFor } from "../lib.jsx";

export default function Login() {
  const { login } = useAuth();
  const nav = useNavigate();
  const demo = useApi("/api/auth/demo-accounts");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState("");

  const go = async (e, p = password) => {
    setErr("");
    setBusy(e);
    try {
      const u = await login(e, p);
      nav(homeFor(u));
    } catch (x) {
      setErr(x.message);
    } finally {
      setBusy("");
    }
  };

  return (
    <div className="login">
      <section className="login-hero">
        <div className="login-hero-inner">
          <p className="login-kicker">Government of Gujarat, Roads &amp; Buildings Department</p>
          <h1>Every road, bridge and building the state maintains, in one register.</h1>
          <p className="login-lede">
            InfraTrack follows each asset from sanction to construction, inspection and repair, and shows every officer the part
            of Gujarat they answer for.
          </p>
          <div className="login-lane" aria-hidden />
          <dl className="login-facts">
            <div><dt>18,492</dt><dd>assets registered</dd></div>
            <div><dt>17</dt><dd>divisions</dd></div>
            <div><dt>50</dt><dd>sub-divisions</dd></div>
          </dl>
        </div>
      </section>

      <section className="login-panel">
        <form
          className="login-form"
          onSubmit={(e) => {
            e.preventDefault();
            go(email, password);
          }}
        >
          <h2>Sign in</h2>
          <label className="field">
            <span>Official email</span>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="name@rnb.gujarat.gov.in" required />
          </label>
          <label className="field">
            <span>Password</span>
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </label>
          {err && <div className="error-box">{err}</div>}
          <button className="btn primary" disabled={!!busy}>Sign in</button>
        </form>

        <div className="demo">
          <h3>Try a role</h3>
          <p className="muted">Each demo account sees only its own jurisdiction. Password for all: demo123</p>
          <div className="demo-grid">
            {(demo.data || []).map((a) => (
              <button key={a.email} className="demo-card" onClick={() => go(a.email, "demo123")} disabled={!!busy}>
                <strong>{a.role_label}</strong>
                <span>{a.name}</span>
                <small>{a.designation}</small>
                {busy === a.email && <em>Signing in…</em>}
              </button>
            ))}
          </div>
          <Link to="/public" className="link">Continue as a member of the public</Link>
        </div>
      </section>
    </div>
  );
}
