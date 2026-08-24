const API_BASE = window.CAMPUS_API_BASE
    || (location.port === "5500" ? "http://localhost:8080/api" : "/api");
let csrfToken = null;
let sessionUser = null;
let sessionUserPromise = null;
let sessionGeneration = 0;

async function ensureCsrf() {
    if (csrfToken) return csrfToken;
    const response = await fetch(`${API_BASE}/auth/csrf`, { credentials: "include" });
    const payload = await response.json();
    csrfToken = payload.data;
    return csrfToken;
}

async function request(path, options = {}) {
    const requestGeneration = sessionGeneration;
    const method = (options.method || "GET").toUpperCase();
    const headers = { ...(options.headers || {}) };
    if (options.body && !(options.body instanceof FormData)) headers["Content-Type"] = "application/json";
    if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
        try {
            headers["X-XSRF-TOKEN"] = await ensureCsrf();
        } catch (_) {
            return { success: false, message: "网络连接失败，请检查网络后重试", data: null };
        }
    }
    let response;
    try {
        response = await fetch(`${API_BASE}${path}`, {
            ...options,
            method,
            headers,
            credentials: "include"
        });
    } catch (_) {
        return { success: false, message: "网络连接失败，请检查网络后重试", data: null };
    }
    const text = await response.text();
    let payload = null;
    if (text) {
        try { payload = JSON.parse(text); }
        catch (_) { payload = { success: false, message: `HTTP ${response.status}` }; }
    }
    if (response.status === 401 && requestGeneration === sessionGeneration) invalidateSession();
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
            const generation = sessionGeneration;
            sessionUserPromise = request("/users/me").then(result => {
                if (generation !== sessionGeneration) return sessionUser;
                sessionUser = result && result.success ? result.data : null;
                return sessionUser;
            });
        }
        return sessionUserPromise;
    },
    async login(email, password) {
        const generation = ++sessionGeneration;
        sessionUser = null;
        sessionUserPromise = null;
        const result = await request("/auth/login", { method: "POST", body: JSON.stringify({ email, password }) });
        if (generation !== sessionGeneration) return result;
        sessionUser = result && result.success ? result.data : null;
        sessionUserPromise = sessionUser ? Promise.resolve(sessionUser) : null;
        applyRoleNavigation(sessionUser);
        return result;
    },
    async logout() {
        ++sessionGeneration;
        sessionUser = null;
        sessionUserPromise = null;
        applyRoleNavigation(null);
        const result = await request("/auth/logout", { method: "POST" });
        csrfToken = null;
        return result;
    },
    set(user) {
        sessionUser = user;
        sessionUserPromise = Promise.resolve(user);
        applyRoleNavigation(user);
    }
};

function invalidateSession() {
    ++sessionGeneration;
    sessionUser = null;
    sessionUserPromise = null;
    applyRoleNavigation(null);
}

function applyRoleNavigation(user) {
    const isAdmin = user && user.role === "ADMIN";
    document.querySelectorAll("[data-admin-only]").forEach(link => {
        link.hidden = !isAdmin;
    });
    document.documentElement.dataset.sessionRole = user?.role || "GUEST";
}

async function hydrateRoleNavigation() {
    applyRoleNavigation(null);
    const user = await session.current();
    applyRoleNavigation(user);
    return user;
}

function formToJson(form) { return Object.fromEntries(new FormData(form).entries()); }

function productImageUrl(value) {
    if (/^\/media\/product-images\/[1-9]\d*\/[0-9a-fA-F-]{36}\.(jpg|png)$/.test(value || "")) {
        return `${API_BASE}${value}`;
    }
    if (/^assets\/images\/[a-zA-Z0-9_-]+\.svg$/.test(value || "")) return value;
    return "assets/images/placeholder.svg";
}

function installImageFallbacks(root = document) {
    root.querySelectorAll("img").forEach(image => {
        image.addEventListener("error", () => {
            image.src = "assets/images/placeholder.svg";
        }, { once: true });
    });
}

async function uploadProductImage(file) {
    const body = new FormData();
    body.append("file", file);
    return request("/media/product-images", { method: "POST", body });
}

function validateProductImageFile(file) {
    if (!file) return null;
    if (!["image/jpeg", "image/png"].includes(file.type)) return "请选择 JPG 或 PNG 图片";
    if (file.size > 5 * 1024 * 1024) return "图片不能超过 5MB";
    return null;
}

window.request = request;
window.formToJson = formToJson;
window.session = session;
window.productImageUrl = productImageUrl;
window.installImageFallbacks = installImageFallbacks;
window.uploadProductImage = uploadProductImage;
window.validateProductImageFile = validateProductImageFile;
window.hydrateRoleNavigation = hydrateRoleNavigation;

hydrateRoleNavigation();
