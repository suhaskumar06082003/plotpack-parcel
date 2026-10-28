import { useState } from "react";
import { api, newKey, rupees } from "../api.js";
import { ITEMS } from "../shoppers.js";

/**
 * Upper-floor executive: identify the customer (user-service), bill the items (estimated bill),
 * then send each item down as a package (package-service allocates a bin and issues the e-token).
 */
export default function FloorExecutive({ onPulse, onChanged }) {
  const [floor, setFloor] = useState(2);
  const [phone, setPhone] = useState("");
  const [name, setName] = useState("");
  const [customer, setCustomer] = useState(null);
  const [customerError, setCustomerError] = useState("");
  const [desc, setDesc] = useState("");
  const [amount, setAmount] = useState("");
  const [cart, setCart] = useState([]);
  const [sending, setSending] = useState(false);
  const [replayNote, setReplayNote] = useState("");

  const bill = cart.reduce((s, i) => s + Number(i.amount), 0);
  const done = cart.filter((i) => i.status === "done");
  const token = done.at(-1)?.result.token;

  async function findCustomer(e) {
    e.preventDefault();
    setCustomerError("");
    try {
      const c = await api.registerCustomer(name.trim() || "Walk-in customer", phone.trim());
      setCustomer(c);
      setName(c.name);
      onPulse("register");
    } catch (err) {
      setCustomerError(err.message);
    }
  }

  function addItem(e) {
    e.preventDefault();
    if (!desc.trim() || !(Number(amount) > 0)) return;
    // The Idempotency-Key is fixed when the item is billed, so any retry of this item reuses it.
    setCart((c) => [...c, { key: newKey(), floor, description: desc.trim(), amount: Number(amount), status: "draft" }]);
    setDesc("");
    setAmount("");
  }

  const update = (key, patch) => setCart((c) => c.map((i) => (i.key === key ? { ...i, ...patch } : i)));

  async function sendDown() {
    setSending(true);
    setReplayNote("");
    for (const item of cart.filter((i) => i.status !== "done")) {
      update(item.key, { status: "sending" });
      try {
        const res = await api.createPackage(item.key, {
          customerId: customer.id, floor: item.floor, description: item.description, amount: item.amount,
        });
        update(item.key, { status: "done", result: res.data });
        onPulse("allocate");
      } catch (err) {
        update(item.key, { status: "error", error: `${err.code || "ERROR"}: ${err.message}` });
      }
      onChanged();
    }
    setSending(false);
  }

  async function resendSameKey(item) {
    const res = await api.createPackage(item.key, {
      customerId: customer.id, floor: item.floor, description: item.description, amount: item.amount,
    });
    setReplayNote(res.replayed
      ? `Retried with the same Idempotency-Key → HTTP ${res.status}, same package #${res.data.packageId} in ${res.data.binCode}. No duplicate created.`
      : `Unexpected: new package #${res.data.packageId}`);
  }

  function nextCustomer() {
    setCustomer(null);
    setPhone("");
    setName("");
    setCart([]);
    setReplayNote("");
  }

  return (
    <div className="stack">
      <div className="floor-pick">
        <span className="label">You are on</span>
        {[1, 2, 3].map((f) => (
          <button key={f} className={floor === f ? "on" : ""} onClick={() => setFloor(f)}>Floor {f}</button>
        ))}
      </div>

      <Step n={1} title="Customer" done={!!customer}>
        {customer ? (
          <div className="customer-card">
            <div><b>{customer.name}</b><small>{customer.phone} · customer #{customer.id}</small></div>
            {cart.length === 0 && <button className="link" onClick={nextCustomer}>change</button>}
          </div>
        ) : (
          <form className="form-row" onSubmit={findCustomer}>
            <input placeholder="Mobile number" value={phone} inputMode="numeric" maxLength={10}
                   onChange={(e) => setPhone(e.target.value.replace(/\D/g, ""))} required />
            <input placeholder="Name (if new)" value={name} onChange={(e) => setName(e.target.value)} />
            <button className="primary" disabled={phone.length !== 10}>Find / register</button>
          </form>
        )}
        {customerError && <p className="error">{customerError}</p>}
      </Step>

      <Step n={2} title="Bill items" done={cart.length > 0} disabled={!customer}>
        <form className="form-row" onSubmit={addItem}>
          <input list="items" placeholder="Item" value={desc} onChange={(e) => setDesc(e.target.value)} disabled={!customer} />
          <datalist id="items">{ITEMS.map((i) => <option key={i.name} value={i.name} />)}</datalist>
          <input placeholder="₹ Price" inputMode="decimal" value={amount} className="narrow"
                 onChange={(e) => setAmount(e.target.value.replace(/[^\d.]/g, ""))} disabled={!customer} />
          <button disabled={!customer}>Add</button>
        </form>
        {cart.length > 0 && (
          <table className="cart">
            <tbody>
              {cart.map((i) => (
                <tr key={i.key} className={i.status}>
                  <td>F{i.floor}</td>
                  <td>{i.description}</td>
                  <td className="num">{rupees(i.amount)}</td>
                  <td className="state">
                    {i.status === "draft" && <span className="tag">billed</span>}
                    {i.status === "sending" && <span className="tag busy">allocating…</span>}
                    {i.status === "done" && (
                      <span className="tag ok" title="Click to retry with the same Idempotency-Key"
                            onClick={() => resendSameKey(i)}>
                        bin {i.result.binCode} {i.result.newBin ? "· new" : "· same bin"}
                      </span>
                    )}
                    {i.status === "error" && <span className="tag bad" title={i.error}>{i.error}</span>}
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr><td /><td>Estimated bill</td><td className="num"><b>{rupees(bill)}</b></td><td /></tr>
            </tfoot>
          </table>
        )}
      </Step>

      <Step n={3} title="Send packages to the ground floor" done={done.length > 0 && done.length === cart.length}
            disabled={!customer || cart.length === 0}>
        <div className="row">
          <button className="primary big" disabled={sending || !customer || cart.every((i) => i.status === "done")}
                  onClick={sendDown}>
            {sending ? "Allocating bins…" : "Send down & allocate bins"}
          </button>
          {done.length > 0 && <button onClick={nextCustomer}>Next customer</button>}
        </div>
        {token && (
          <div className="token-issued">
            <small>E-token sent to {customer.phone} by SMS</small>
            <b>{token}</b>
            <span>{done.at(-1).result.tokenPackageCount} package(s) · estimated bill {rupees(done.at(-1).result.estimatedBill)}</span>
            <em>Tip: click a green “bin” tag to resend that request with the same Idempotency-Key.</em>
          </div>
        )}
        {replayNote && <p className="note">{replayNote}</p>}
      </Step>
    </div>
  );
}

function Step({ n, title, done, disabled, children }) {
  return (
    <div className={`step ${done ? "done" : ""} ${disabled ? "disabled" : ""}`}>
      <div className="step-head"><span className="num-badge">{done ? "✓" : n}</span><h3>{title}</h3></div>
      <div className="step-body">{children}</div>
    </div>
  );
}
