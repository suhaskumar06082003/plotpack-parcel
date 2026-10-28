import { useEffect, useRef, useState } from "react";
import { api, newKey } from "../api.js";
import { basketSize, randomCustomer, randomFloor, randomItem, sleep } from "../shoppers.js";

/**
 * Real-time store simulation driven from the browser, using exactly the same public APIs as the
 * floor and counter screens. Plus one-click drills for the edge cases discussed in the interview.
 */
export default function Simulation({ onPulse, onChanged }) {
  const [running, setRunning] = useState(false);
  const [rate, setRate] = useState(6);          // customers per minute
  const [counts, setCounts] = useState({ customers: 0, packages: 0, delivered: 0, rejected: 0 });
  const [log, setLog] = useState([]);
  const [busy, setBusy] = useState(null);
  const runningRef = useRef(false);
  const rateRef = useRef(rate);
  rateRef.current = rate;

  const bump = (k, n = 1) => setCounts((c) => ({ ...c, [k]: c[k] + n }));
  const addLog = (entry) => setLog((l) => [{ id: newKey(), at: new Date(), ...entry }, ...l].slice(0, 30));

  // ---------------------------------------------------------------- autopilot
  useEffect(() => {
    runningRef.current = running;
    if (!running) return;
    let stop = false;
    (async () => {
      while (!stop && runningRef.current) {
        journey();
        await sleep(60000 / rateRef.current);
      }
    })();
    return () => { stop = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [running]);

  async function journey() {
    try {
      const { name, phone } = randomCustomer();
      const customer = await api.registerCustomer(name, phone);
      bump("customers");
      let token = null;
      for (let i = 0; i < basketSize(); i++) {
        await sleep(800 + Math.random() * 1500);           // walking between counters upstairs
        try {
          const res = await api.createPackage(newKey(), { customerId: customer.id, floor: randomFloor(), ...randomItem() });
          token = res.data.token;
          bump("packages");
          onPulse("allocate");
        } catch (err) {
          bump("rejected");
          addLog({ ok: false, title: `${name}: ${err.code}`, text: err.message });
        }
      }
      if (!token) return;
      await sleep(8000 + Math.random() * 12000);           // pays, walks down to the ground floor
      await api.deliver(token);
      bump("delivered");
      onPulse("deliver");
    } catch (err) {
      addLog({ ok: false, title: "Journey error", text: err.message });
    } finally {
      onChanged();
    }
  }

  // ---------------------------------------------------------------- drills
  async function drill(name, fn) {
    setBusy(name);
    try {
      addLog(await fn());
    } catch (err) {
      addLog({ ok: false, title: name, text: err.message });
    } finally {
      setBusy(null);
      onChanged();
    }
  }

  const register = (tag) => {
    const c = randomCustomer();
    return api.registerCustomer(`${c.name} (${tag})`, c.phone);
  };
  const settle = (promises) => Promise.allSettled(promises);

  const binRace = () => drill("race", async () => {
    const customers = await Promise.all(Array.from({ length: 10 }, () => register("race")));
    const results = await settle(customers.map((c) =>
      api.createPackage(newKey(), { customerId: c.id, floor: randomFloor(), ...randomItem() })));
    const ok = results.filter((r) => r.status === "fulfilled").map((r) => r.value.data);
    const bins = new Set(ok.map((p) => p.binCode));
    onPulse("allocate");
    return {
      ok: bins.size === ok.length,
      title: "Bin race: 10 new customers at the same instant",
      text: `${ok.length} allocated into ${bins.size} different bins → ${bins.size === ok.length ? "no bin given twice" : "DOUBLE ALLOCATION!"}. `
        + "Redis SETNX lock per bin + UNIQUE(active_slot) in MySQL.",
    };
  });

  const sameCustomer = () => drill("same", async () => {
    const c = await register("3 floors");
    const results = await settle([1, 2, 3, 1, 2, 3].map((floor) =>
      api.createPackage(newKey(), { customerId: c.id, floor, ...randomItem() })));
    const ok = results.filter((r) => r.status === "fulfilled").map((r) => r.value.data);
    const tokens = new Set(ok.map((p) => p.token));
    const view = await api.token([...tokens][0]);
    const perBin = view.bins.map((b) => `${b.binCode}×${b.items.length}`).join(", ");
    const overfilled = view.bins.some((b) => b.items.length > 4);
    onPulse("allocate");
    return {
      ok: tokens.size === 1 && !overfilled && ok.length === 6,
      title: "Same customer, 6 items from 3 floors at once",
      text: `${ok.length}/6 created · ${tokens.size} e-token · bins: ${perBin} (capacity 4). `
        + "Per-customer Redis lock + PESSIMISTIC_WRITE row lock on the bin count.",
    };
  });

  const duplicate = () => drill("dup", async () => {
    const c = await register("retry");
    const key = newKey();
    const item = { customerId: c.id, floor: 2, ...randomItem() };
    const results = await settle(Array.from({ length: 5 }, () => api.createPackage(key, item)));
    const ok = results.filter((r) => r.status === "fulfilled").map((r) => r.value);
    const ids = new Set(ok.map((r) => r.data.packageId));
    onPulse("allocate");
    return {
      ok: ids.size === 1,
      title: "Same request sent 5× (one Idempotency-Key)",
      text: `HTTP ${ok.map((r) => r.status).join(", ")} → ${ids.size} package created. `
        + "Redis fast path + UNIQUE(idempotency_key) in MySQL.",
    };
  });

  const doubleHandover = () => drill("double", async () => {
    const c = await register("double tap");
    const p = await api.createPackage(newKey(), { customerId: c.id, floor: 1, ...randomItem() });
    const results = await settle([api.deliver(p.data.token), api.deliver(p.data.token)]);
    const ok = results.filter((r) => r.status === "fulfilled").length;
    const conflict = results.find((r) => r.status === "rejected")?.reason;
    onPulse("deliver");
    return {
      ok: ok === 1 && conflict?.code === "ALREADY_DELIVERED",
      title: "Two counter staff deliver the same e-token at once",
      text: `${ok} delivery succeeded, the other got ${conflict?.code ?? "nothing"}. Row locks make release happen exactly once.`,
    };
  });

  const fillGrid = () => drill("fill", async () => {
    let rejected = null;
    let created = 0;
    for (let round = 0; round < 10 && !rejected; round++) {
      const customers = await Promise.all(Array.from({ length: 8 }, () => register("rush")));
      const results = await settle(customers.map((c) =>
        api.createPackage(newKey(), { customerId: c.id, floor: randomFloor(), ...randomItem() })));
      created += results.filter((r) => r.status === "fulfilled").length;
      rejected = results.find((r) => r.status === "rejected")?.reason ?? null;
      onChanged();
    }
    onPulse("allocate");
    return {
      ok: rejected?.code === "NO_FREE_BIN",
      title: "Festival rush until the grid is full",
      text: `${created} more customers placed, then: ${rejected ? `${rejected.status} ${rejected.code} – ${rejected.message}` : "grid never filled"}`,
    };
  });

  const deliverAll = () => drill("drain", async () => {
    const tokens = await api.waitingTokens();
    const results = await settle(tokens.map((t) => api.deliver(t.token)));
    onPulse("deliver");
    return {
      ok: true,
      title: "Everyone collects",
      text: `${results.filter((r) => r.status === "fulfilled").length} e-tokens delivered, bins released.`,
    };
  });

  return (
    <div className="stack">
      <div className="autopilot">
        <div>
          <h3>Live store traffic</h3>
          <p className="muted">Customers arrive, buy 1–5 items across floors, get an e-token, then collect downstairs.</p>
        </div>
        <label className="slider">
          <span>{rate} customers / min</span>
          <input type="range" min="2" max="40" value={rate} onChange={(e) => setRate(Number(e.target.value))} />
        </label>
        <button className={running ? "danger" : "primary"} onClick={() => setRunning((r) => !r)}>
          {running ? "Stop traffic" : "Start traffic"}
        </button>
        <div className="counters">
          <span><b>{counts.customers}</b>customers</span>
          <span><b>{counts.packages}</b>packages</span>
          <span><b>{counts.delivered}</b>delivered</span>
          <span><b>{counts.rejected}</b>rejected</span>
        </div>
      </div>

      <div>
        <h3 className="section-title">Edge-case drills</h3>
        <div className="drills">
          <Drill busy={busy === "race"} onClick={binRace} title="Bin race" text="10 new customers, same instant" />
          <Drill busy={busy === "same"} onClick={sameCustomer} title="3 floors, 1 customer" text="6 items at once" />
          <Drill busy={busy === "dup"} onClick={duplicate} title="Duplicate request" text="same Idempotency-Key ×5" />
          <Drill busy={busy === "double"} onClick={doubleHandover} title="Double hand-over" text="same e-token twice" />
          <Drill busy={busy === "fill"} onClick={fillGrid} title="Grid full" text="rush until 503 NO_FREE_BIN" />
          <Drill busy={busy === "drain"} onClick={deliverAll} title="Everyone collects" text="deliver all waiting tokens" />
        </div>
      </div>

      <ol className="sim-log">
        {log.length === 0 && <li className="muted">Results appear here.</li>}
        {log.map((e) => (
          <li key={e.id} className={e.ok ? "pass" : "fail"}>
            <span className="verdict">{e.ok ? "PASS" : "FAIL"}</span>
            <div><b>{e.title}</b><p>{e.text}</p></div>
          </li>
        ))}
      </ol>
    </div>
  );
}

function Drill({ title, text, onClick, busy }) {
  return (
    <button className="drill" onClick={onClick} disabled={busy}>
      <b>{busy ? "Running…" : title}</b>
      <small>{text}</small>
    </button>
  );
}
