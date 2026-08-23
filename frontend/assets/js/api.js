const API_BASE = window.CAMPUS_API_BASE
    || (location.port === "5500" ? "http://localhost:8080/api" : "/api");
let csrfToken = null;
let sessionUser = null;
let sessionUserPromise = null;

async function ensureCsrf() {
    if (csrfToken) return csrfToken;
    const response = await fetch(`${API_BASE}/auth/csrf`, { credentials: "include" });
    const payload = await response.json();
    csrfToken = payload.data;
    return csrfToken;
}

async function request(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const headers = { ...(options.headers || {}) };
    if (options.body) headers["Content-Type"] = "application/json";
    if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
        headers["X-XSRF-TOKEN"] = await ensureCsrf();
    }
    const response = await fetch(`${API_BASE}${path}`, {
        ...options,
        method,
        headers,
        credentials: "include"
    });
    const text = await response.text();
    let payload = null;
    if (text) {
        try { payload = JSON.parse(text); }
        catch (_) { payload = { success: false, message: `HTTP ${response.status}` }; }
    }
    if (response.status === 401) {
        sessionUser = null;
        sessionUserPromise = null;
    }
    if (!response.ok && !(payload && typeof payload === "object")) {
        payload = { success: false, message: `HTTP ${response.status}` };
    }
    return payload;
}

const session = {
    async current({ refresh = false } = {}) {
        if (refresh) sessionUserPromise = null;
        if (sessionUser) return sessionUser;
        if (!sessionUserPromise) {
            sessionUserPromise = request("/users/me").then(result => {
                sessionUser = result && result.success ? result.data : null;
                return sessionUser;
            });
        }
        return sessionUserPromise;
    },
    async login(email, password) {
        const result = await request("/auth/login", { method: "POST", body: JSON.stringify({ email, password }) });
        sessionUser = result && result.success ? result.data : null;
        sessionUserPromise = sessionUser ? Promise.resolve(sessionUser) : null;
        return result;
    },
    async logout() {
        const result = await request("/auth/logout", { method: "POST" });
        sessionUser = null;
        sessionUserPromise = null;
        csrfToken = null;
        return result;
    },
    set(user) {
        sessionUser = user;
        sessionUserPromise = Promise.resolve(user);
    }
};

function formToJson(form) { return Object.fromEntries(new FormData(form).entries()); }

window.request = request;
window.formToJson = formToJson;
window.session = session;
