import { useEffect, useState } from "react";
import { api } from "../api.js";

/**
 * Stands in for the customers' phones: every SMS notification-service sends (after consuming
 * a Kafka event) arrives here live over Server-Sent Events.
 */
export default function CustomerPhone({ onTokenClick }) {
  const [messages, setMessages] = useState([]);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    api.notifications().then(setMessages).catch(() => {});
    const es = new EventSource("/api/notifications/stream");
    es.addEventListener("hello", () => setConnected(true));
    es.addEventListener("notification", (e) => {
      const n = JSON.parse(e.data);
      setMessages((list) => [{ ...n, fresh: true }, ...list.filter((m) => m.id !== n.id)].slice(0, 40));
    });
    es.onerror = () => setConnected(false);
    return () => es.close();
  }, []);

  return (
    <section className="panel phone-panel">
      <div className="phone">
        <div className="phone-top">
          <span>SMS · customers</span>
          <span className={`dot ${connected ? "on" : ""}`} title={connected ? "live" : "reconnecting"} />
        </div>
        <ol className="sms-list">
          {messages.length === 0 && <li className="empty">No messages yet. Create a package to send an e-token.</li>}
          {messages.map((m) => (
            <li key={m.id} className={`sms ${m.kind} ${m.fresh ? "fresh" : ""}`}>
              <div className="sms-meta">
                <b>{m.customerName}</b>
                <time>{new Date(m.createdAt).toLocaleTimeString([], { hour12: false })}</time>
              </div>
              <p>{m.message}</p>
              {m.kind === "E_TOKEN" && (
                <button className="token-badge" onClick={() => onTokenClick(m.token)} title="Customer shows this at the counter">
                  {m.token} → pickup
                </button>
              )}
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
