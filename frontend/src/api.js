// Every call goes to the API gateway (/api/...). Errors carry the backend's error code.

async function request(method, path, body, headers = {}) {
  const res = await fetch(path, {
    method,
    headers: { ...(body ? { "Content-Type": "application/json" } : {}), ...headers },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }
  if (!res.ok) {
    const err = new Error(data?.message || `HTTP ${res.status}`);
    err.status = res.status;
    err.code = data?.code;
    throw err;
  }
  return { status: res.status, data, replayed: res.headers.get("Idempotent-Replayed") === "true" };
}

export const api = {
  // user-service
  registerCustomer: (name, phone) => request("POST", "/api/users", { name, phone }).then((r) => r.data),

  // package-service
  createPackage: (idempotencyKey, pkg) =>
    request("POST", "/api/packages", pkg, { "Idempotency-Key": idempotencyKey }),
  token: (token) => request("GET", `/api/packages/tokens/${encodeURIComponent(token)}`).then((r) => r.data),
  deliver: (token) =>
    request("POST", `/api/packages/tokens/${encodeURIComponent(token)}/deliver`).then((r) => r.data),
  waitingTokens: () => request("GET", "/api/packages/tokens").then((r) => r.data),
  grid: () => request("GET", "/api/packages/bins").then((r) => r.data),
  stats: () => request("GET", "/api/packages/stats").then((r) => r.data),

  // notification-service
  notifications: () => request("GET", "/api/notifications?limit=25").then((r) => r.data),
};

export const newKey = () =>
  (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`);

export const rupees = (n) => `₹${Number(n || 0).toLocaleString("en-IN")}`;
