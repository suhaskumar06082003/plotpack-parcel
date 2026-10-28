import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "./api.js";
import FlowStrip from "./components/FlowStrip.jsx";
import FloorExecutive from "./components/FloorExecutive.jsx";
import PickupCounter from "./components/PickupCounter.jsx";
import Simulation from "./components/Simulation.jsx";
import BinGrid from "./components/BinGrid.jsx";
import CustomerPhone from "./components/CustomerPhone.jsx";

const TABS = [
  { id: "floor", label: "Floor executive", hint: "Upper floors" },
  { id: "pickup", label: "Pickup counter", hint: "Ground floor" },
  { id: "sim", label: "Simulation", hint: "Rush hour & edge cases" },
];

export default function App() {
  const [tab, setTab] = useState("floor");
  const [grid, setGrid] = useState([]);
  const [stats, setStats] = useState(null);
  const [online, setOnline] = useState(true);
  const [highlight, setHighlight] = useState([]);
  const [pickupToken, setPickupToken] = useState("");
  const [flow, setFlow] = useState({ kind: null, seq: 0 });

  // The grid is cheap to read; polling every second keeps the UI honest without extra infrastructure.
  const refresh = useCallback(async () => {
    try {
      const [g, s] = await Promise.all([api.grid(), api.stats()]);
      setGrid(g);
      setStats(s);
      setOnline(true);
    } catch {
      setOnline(false);
    }
  }, []);

  useEffect(() => {
    refresh();
    const t = setInterval(refresh, 1000);
    return () => clearInterval(t);
  }, [refresh]);

  const pulse = useCallback((kind) => setFlow((f) => ({ kind, seq: f.seq + 1 })), []);

  const openToken = (token) => {
    setPickupToken(token);
    setTab("pickup");
  };

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <div className="mark" aria-hidden="true"><i /><i /><i /><i /></div>
          <div>
            <h1>PlotPack</h1>
            <p>Jayalakshmi Silks · ground-floor pickup grid</p>
          </div>
        </div>
        <div className="chips">
          <Chip label="Bins in use" value={stats ? `${stats.occupiedBins}/${stats.totalBins}` : "–"} accent />
          <Chip label="Packages waiting" value={stats?.packagesWaiting ?? "–"} />
          <Chip label="Delivered today" value={stats?.deliveredToday ?? "–"} />
          <span className={`live ${online ? "on" : "off"}`}><i />{online ? "backend live" : "backend offline"}</span>
        </div>
      </header>

      <FlowStrip flow={flow} />

      <main className="columns">
        <section className="panel console">
          <nav className="tabs" role="tablist">
            {TABS.map((t) => (
              <button key={t.id} role="tab" aria-selected={tab === t.id}
                      className={tab === t.id ? "active" : ""} onClick={() => setTab(t.id)}>
                <b>{t.label}</b><small>{t.hint}</small>
              </button>
            ))}
          </nav>
          {/* All screens stay mounted so traffic keeps running and forms keep their state when switching tabs. */}
          <div className="tab-body" hidden={tab !== "floor"}>
            <FloorExecutive onPulse={pulse} onChanged={refresh} />
          </div>
          <div className="tab-body" hidden={tab !== "pickup"}>
            <PickupCounter initialToken={pickupToken} onHighlight={setHighlight} onPulse={pulse} onChanged={refresh} />
          </div>
          <div className="tab-body" hidden={tab !== "sim"}>
            <Simulation onPulse={pulse} onChanged={refresh} />
          </div>
        </section>

        <BinGrid cells={grid} highlight={highlight} />

        <CustomerPhone onTokenClick={openToken} />
      </main>

      <footer className="foot">
        React → API Gateway → user-service · package-service (MySQL + Redis) → Kafka → notification-service
      </footer>
    </div>
  );
}

function Chip({ label, value, accent }) {
  const ref = useRef(null);
  const prev = useRef(value);
  useEffect(() => {
    if (prev.current !== value && ref.current) {
      ref.current.classList.remove("bump");
      void ref.current.offsetWidth;
      ref.current.classList.add("bump");
    }
    prev.current = value;
  }, [value]);
  return (
    <div className={`chip ${accent ? "accent" : ""}`}>
      <small>{label}</small>
      <b ref={ref}>{value}</b>
    </div>
  );
}
