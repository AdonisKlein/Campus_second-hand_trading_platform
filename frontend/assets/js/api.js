const API_BASE = window.CAMPUS_API_BASE
    || (location.port === "5500" ? "http://localhost:8080/api" : "/api");
let csrfToken = null;
let sessionUser = null;
let sessionUserPromise = null;
let sessionGeneration = 0;
let authConfirmResolver = null;

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

function safeReturnTarget(value) {
    if (!value || /^(?:[a-z][a-z0-9+.-]*:|\/\/)/i.test(value)) return "index.html";
    return value.startsWith("/") || /^[a-zA-Z0-9_-]+\.html(?:[?#].*)?$/.test(value) ? value : "index.html";
}

function authenticationDialog() {
    let dialog = document.querySelector("#authenticationPrompt");
    if (dialog) return dialog;
    dialog = document.createElement("dialog");
    dialog.id = "authenticationPrompt";
    dialog.className = "auth-confirm-dialog";
    dialog.innerHTML = `
        <div class="auth-confirm-content">
            <h2>登录后继续操作</h2>
            <p data-auth-message>发布、留言和交易功能需要登录校园账号。</p>
            <div class="auth-confirm-actions"><button type="button" class="secondary" data-auth-cancel>暂不登录</button><button type="button" data-auth-confirm>前往登录</button></div>
        </div>`;
    dialog.querySelector("[data-auth-cancel]").addEventListener("click", () => dialog.close("cancel"));
    dialog.querySelector("[data-auth-confirm]").addEventListener("click", () => dialog.close("confirm"));
    dialog.addEventListener("close", () => {
        const resolver = authConfirmResolver;
        authConfirmResolver = null;
        if (resolver) resolver(dialog.returnValue === "confirm");
    });
    document.body.append(dialog);
    return dialog;
}

function confirmAuthentication(message = "此操作需要登录校园账号，是否前往登录？") {
    const dialog = authenticationDialog();
    dialog.querySelector("[data-auth-message]").textContent = message;
    if (dialog.open) dialog.close("cancel");
    return new Promise(resolve => {
        authConfirmResolver = resolve;
        dialog.showModal();
    });
}

function redirectToLogin(returnTo) {
    sessionStorage.setItem("postLoginTarget", safeReturnTarget(returnTo));
    location.href = "profile.html";
}

function redirectToLoginWithMessage(message) {
    sessionStorage.setItem("authSuccessMessage", message);
    sessionStorage.removeItem("postLoginTarget");
    location.href = "profile.html";
}

async function requireAuthenticatedUser({ message, returnTo = location.pathname + location.search } = {}) {
    const user = await session.current();
    if (user && user.id) return user;
    if (await confirmAuthentication(message)) redirectToLogin(returnTo);
    return null;
}

function consumePostLoginTarget() {
    const target = sessionStorage.getItem("postLoginTarget");
    sessionStorage.removeItem("postLoginTarget");
    return target ? safeReturnTarget(target) : null;
}

function reportDialog() {
    let dialog = document.querySelector("#contentReportDialog");
    if (dialog) return dialog;
    dialog = document.createElement("dialog");
    dialog.id = "contentReportDialog";
    dialog.className = "report-dialog";
    dialog.innerHTML = `
        <form method="dialog" class="report-dialog-form">
            <h2 id="contentReportTitle">提交举报</h2>
            <p data-report-summary></p>
            <label>举报原因<select name="reasonCode" required><option value="FRAUD">疑似诈骗或虚假信息</option><option value="PROHIBITED_CONTENT">违规内容</option><option value="HARASSMENT">骚扰或不友善行为</option><option value="SPAM">垃圾广告</option><option value="OTHER">其他问题</option></select></label>
            <label>具体说明<textarea name="description" minlength="10" maxlength="1000" rows="5" required placeholder="请用至少 10 个字说明问题，便于管理员核查"></textarea></label>
            <p class="form-message" data-report-message role="status" aria-live="polite"></p>
            <div class="report-dialog-actions"><button type="button" class="secondary" data-report-cancel>取消</button><button type="submit">提交举报</button></div>
        </form>`;
    dialog.querySelector("[data-report-cancel]").addEventListener("click", () => dialog.close());
    dialog.setAttribute("aria-labelledby", "contentReportTitle");
    document.body.append(dialog);
    return dialog;
}

async function openReportDialog(targetType, targetId, summary = "该内容") {
    const user = await requireAuthenticatedUser({ message: "登录后才能提交举报，是否前往登录？", returnTo: location.pathname + location.search });
    if (!user) return false;
    const dialog = reportDialog();
    const form = dialog.querySelector("form");
    form.reset();
    dialog.querySelector("[data-report-summary]").textContent = `举报对象：${summary}`;
    dialog.querySelector("[data-report-message]").textContent = "";
    dialog.showModal();
    return new Promise(resolve => {
        let submitted = false;
        const submit = async event => {
            event.preventDefault();
            const button = form.querySelector('button[type="submit"]');
            button.disabled = true;
            const data = formToJson(form);
            const result = await request("/reports", { method: "POST", body: JSON.stringify({
                targetType, targetId: Number(targetId), reasonCode: data.reasonCode, description: data.description
            }) });
            button.disabled = false;
            if (!result.success) { dialog.querySelector("[data-report-message]").textContent = result.message || "举报提交失败"; return; }
            submitted = true;
            form.removeEventListener("submit", submit);
            dialog.close();
        };
        form.addEventListener("submit", submit, { once: false });
        dialog.addEventListener("close", () => { form.removeEventListener("submit", submit); resolve(submitted); }, { once: true });
    });
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
    if (user && user.role === "STUDENT") refreshChatUnread();
    return user;
}

async function refreshChatUnread() {
    const badges = document.querySelectorAll("[data-chat-unread]");
    if (!badges.length || !sessionUser || sessionUser.role !== "STUDENT") return;
    const result = await request("/chat/unread-count");
    const count = result?.success ? Number(result.data?.count || 0) : 0;
    badges.forEach(badge => { badge.hidden = count < 1; badge.textContent = count > 99 ? "99+" : String(count); });
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
window.confirmAuthentication = confirmAuthentication;
window.requireAuthenticatedUser = requireAuthenticatedUser;
window.consumePostLoginTarget = consumePostLoginTarget;
window.redirectToLoginWithMessage = redirectToLoginWithMessage;
window.openReportDialog = openReportDialog;
window.refreshChatUnread = refreshChatUnread;

document.addEventListener("click", event => {
    const link = event.target.closest("a[data-requires-auth]");
    if (!link || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey) return;
    event.preventDefault();
    requireAuthenticatedUser({ message: "登录后才能使用发布和订单功能，是否前往登录？", returnTo: link.getAttribute("href") })
        .then(user => { if (user) location.href = link.href; });
});

hydrateRoleNavigation();
