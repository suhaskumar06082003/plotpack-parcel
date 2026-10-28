import { useEffect, useState } from "react";
import { api, rupees } from "../api.js";

/**
 * Ground floor: the customer tells their e-token -> system shows which bins to open ->
 * staff hand over -> bins are released and a "thank you" SMS goes out via Kafka.
 */
export default function PickupCounter({ initialToken, onHighlight, onPulse, onChanged }) {
  const [token, setToken] = useState(initialToken || "");
  const [view, setView] = useState(null);
  const [error, setError] = useState("");
  const [result, setResult] = useState(null);
  const [lookupMs, setLookupMs] = useState(null);
  const [waiting, setWaiting] = useState([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const load = () => api.waitingTokens().then(setWaiting).catch(() => {});
    load();
    const t = setInterval(load, 2500);
    return () => clearInterval(t);
  }, []);

  useEffect(() => {
    if (initialToken) lookup(initialToken);
    return () => onHighlight([]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialToken]);

  async function lookup(value) {
    const t = (value ?? token).trim().toUpperCase();
    if (!t) return;
    setToken(t);
    setError("");
    setResult(null);
    const started = performance.now();
    try {
      const v = await api.token(t);
      setLookupMs(Math.round(performance.now() - started));
      setView(v);
      onHighlight(v.status === "WAITING" ? v.bins.map((b) => b.binCode) : []);
      onPulse("lookup");
    } catch (err) {
      setView(null);
      onHighlight([]);
      setError(err.message);
    }
  }

  async function handOver() {
    setBusy(true);
    try {
      const r = await api.deliver(view.token);
      setResult(r);
      setView({ ...view, status: "DELIVERED" });
      onHighlight([]);
      onPulse("deliver");
      onChanged();
    } catch (err) {
      setError(`${err.code}: ${err.message}`);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="stack">
      <form className="token-form" onSubmit={(e) => { e.preventDefault(); lookup(); }}>
        <label htmlFor="token">Customer’s e-token</label>
        <div className="form-row">
          <input id="token" className="token-input" placeholder="PP-XXXXXX" value={token}
                 onChange={(e) => setToken(e.target.value.toUpperCase())} autoComplete="off" />
          <button className="primary">Find bins</button>
        </div>
      </form>

      {waiting.length > 0 && (
        <div className="waiting">
          <small>Waiting at the counter (demo helper: tap instead of typing)</small>
          <div className="token-chips">
            {waiting.slice(0, 12).map((w) => (
              <button key={w.token} onClick={() => lookup(w.token)} title={`${w.customerName} · ${w.packageCount} pkg`}>
                {w.token}
              </button>
            ))}
            {waiting.length > 12 && <span className="muted">+{waiting.length - 12} more</span>}
          </div>
        </div>
      )}

      {error && <p className="error">{error}</p>}

      {view && (
        <div className={`pickup-card ${view.status === "DELIVERED" ? "closed" : ""}`}>
          <div className="pickup-head">
            <div>
              <h3>{view.customerName}</h3>
              <small>{view.customerPhone} · {view.packageCount} package(s) · bill {rupees(view.estimatedBill)}</small>
            </div>
            <span className={`status ${view.status}`}>{view.status}</span>
          </div>
          {lookupMs != null && view.status === "WAITING" && (
            <p className="note">Located in {lookupMs} ms. Walk to the highlighted bins, no searching.</p>
          )}
          <div className="open-bins">
            {view.bins.map((b) => (
              <div key={b.binCode} className="open-bin">
                <b>{b.binCode}</b>
                <ul>{b.items.map((i) => <li key={i.packageId}>F{i.floor} · {i.description} · {rupees(i.amount)}</li>)}</ul>
              </div>
            ))}
          </div>
          {view.status === "WAITING" && (
            <button className="primary big" disabled={busy} onClick={handOver}>
              {busy ? "Releasing bins…" : "Hand over & release bins"}
            </button>
          )}
        </div>
      )}

      {result && (
        <div className="delivered">
          <b>✓ {result.deliveredCount} package(s) handed to {result.customerName}</b>
          <span>Bins released back to the pool: {result.binsReleased.join(", ") || "none (still in use)"}</span>
          <span>Kafka → notification-service → “Delivered, thank you for shopping” SMS</span>
        </div>
      )}
    </div>
  );
}
