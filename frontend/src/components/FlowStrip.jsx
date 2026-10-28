import { useEffect, useState } from "react";

// The architecture, drawn as the path a request takes. Nodes light up in order on every action.
const NODES = [
  { id: "ui", title: "React UI", sub: "floor / counter" },
  { id: "gw", title: "API Gateway", sub: ":8090" },
  { id: "user", title: "User Service", sub: "find / register" },
  { id: "pkg", title: "Package Service", sub: "bin + e-token" },
  { id: "kafka", title: "Kafka", sub: "package-events" },
  { id: "notify", title: "Notification", sub: "SMS" },
  { id: "phone", title: "Customer", sub: "phone" },
];

const PATHS = {
  allocate: { nodes: ["ui", "gw", "user", "pkg", "kafka", "notify", "phone"], label: "Package created → bin allocated → e-token SMS" },
  register: { nodes: ["ui", "gw", "user"], label: "Customer looked up / registered" },
  lookup: { nodes: ["ui", "gw", "pkg"], label: "E-token looked up → bins to open" },
  deliver: { nodes: ["ui", "gw", "pkg", "kafka", "notify", "phone"], label: "Handed over → bins released → “thank you” SMS" },
};

const STEP_MS = 260;

export default function FlowStrip({ flow }) {
  const [lit, setLit] = useState([]);
  const [label, setLabel] = useState("Every action lights up the services it passes through");

  useEffect(() => {
    const path = PATHS[flow.kind];
    if (!path) return;
    setLabel(path.label);
    setLit([]);
    const timers = path.nodes.map((id, i) => setTimeout(() => setLit((l) => [...l, id]), i * STEP_MS));
    const clear = setTimeout(() => setLit([]), path.nodes.length * STEP_MS + 1800);
    return () => [...timers, clear].forEach(clearTimeout);
  }, [flow]);

  return (
    <section className="flow" aria-label="Request path">
      <ol>
        {NODES.map((n, i) => (
          <li key={n.id} className={`${lit.includes(n.id) ? "lit" : ""} ${n.id === "kafka" ? "async" : ""}`}>
            {i > 0 && <span className={`wire ${n.id === "kafka" || n.id === "notify" || n.id === "phone" ? "dashed" : ""}`} />}
            <div className="node">
              <b>{n.title}</b>
              <small>{n.sub}</small>
            </div>
          </li>
        ))}
      </ol>
      <p className="flow-label">{label}<span className="legend-async">dashed = asynchronous (Kafka)</span></p>
    </section>
  );
}
