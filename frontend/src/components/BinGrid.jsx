import { useEffect, useRef } from "react";

/** The physical ground-floor grid. Dots = packages in the bin / capacity. */
export default function BinGrid({ cells, highlight }) {
  const racks = {};
  for (const c of cells) (racks[c.rack] ||= []).push(c);
  const occupied = cells.filter((c) => c.packageCount > 0).length;

  return (
    <section className="panel grid-panel">
      <div className="panel-head">
        <div>
          <h2>Ground-floor bins</h2>
          <p className="muted">{occupied} of {cells.length} in use · nearest to the counter fill first</p>
        </div>
        <div className="legend">
          <span><i className="lg free" />free</span>
          <span><i className="lg used" />in use</span>
          <span><i className="lg full" />full</span>
          <span><i className="lg open" />open now</span>
        </div>
      </div>
      <div className="counter-mark">▼ PICKUP COUNTER</div>
      <div className="racks">
        {Object.keys(racks).sort().map((rack) => (
          <div key={rack} className="rack">
            <h4>Rack {rack}</h4>
            <div className="cells" style={{ gridTemplateColumns: `repeat(${Math.max(...racks[rack].map((c) => c.col))}, 1fr)` }}>
              {racks[rack].sort((a, b) => a.row - b.row || a.col - b.col).map((c) => (
                <Cell key={c.binId} cell={c} open={highlight.includes(c.code)} />
              ))}
            </div>
          </div>
        ))}
        {cells.length === 0 && <p className="muted">Waiting for package-service…</p>}
      </div>
    </section>
  );
}

function Cell({ cell, open }) {
  const ref = useRef(null);
  const prev = useRef(cell.packageCount);
  useEffect(() => {
    if (prev.current !== cell.packageCount && ref.current) {
      const cls = cell.packageCount > prev.current ? "flash-in" : "flash-out";
      ref.current.classList.remove("flash-in", "flash-out");
      void ref.current.offsetWidth;
      ref.current.classList.add(cls);
    }
    prev.current = cell.packageCount;
  }, [cell.packageCount]);

  const state = cell.packageCount === 0 ? "free" : cell.packageCount >= cell.capacity ? "full" : "used";
  return (
    <div ref={ref} className={`cell ${state} ${open ? "open" : ""}`}
         title={cell.packageCount ? `${cell.code}: ${cell.packageCount}/${cell.capacity} · ${cell.customerName} · ${cell.token}` : `${cell.code}: free`}>
      <span className="code">{cell.code}</span>
      <span className="who">{cell.customerName ? cell.customerName.split(" ")[0] : ""}</span>
      <span className="dots">
        {Array.from({ length: cell.capacity }, (_, i) => <i key={i} className={i < cell.packageCount ? "on" : ""} />)}
      </span>
    </div>
  );
}
