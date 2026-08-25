const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

function response(status, payload) {
    return {
        status,
        ok: status >= 200 && status < 300,
        async json() { return payload; },
        async text() { return JSON.stringify(payload); }
    };
}

function createHarness() {
    let resolveMe;
    const meResponse = new Promise(resolve => { resolveMe = resolve; });
    const adminLink = { hidden: true };
    const document = {
        documentElement: { dataset: {} },
        addEventListener() {},
        querySelector() { return null; },
        querySelectorAll(selector) {
            if (selector === "[data-admin-only]") return [adminLink];
            if (selector === "img") return [];
            return [];
        }
    };
    const context = {
        document,
        location: { port: "" },
        FormData: class FormData {},
        URLSearchParams,
        console,
        fetch: async url => {
            if (url.endsWith("/users/me")) return meResponse;
            if (url.endsWith("/auth/csrf")) return response(200, { success: true, data: "csrf" });
            if (url.endsWith("/auth/login")) return response(200, {
                success: true,
                data: { id: 1, email: "admin@example.com", role: "ADMIN" }
            });
            if (url.endsWith("/auth/logout")) return response(200, { success: true });
            throw new Error(`Unexpected fetch ${url}`);
        }
    };
    context.window = context;
    vm.createContext(context);
    const apiSource = fs.readFileSync(path.resolve(__dirname, "../assets/js/api.js"), "utf8");
    vm.runInContext(apiSource, context, { filename: "api.js" });
    return { context, adminLink, resolveMe };
}

(async () => {
    const loginHarness = createHarness();
    await loginHarness.context.session.login("admin@example.com", "password");
    loginHarness.resolveMe(response(401, { success: false, message: "未登录" }));
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(loginHarness.adminLink.hidden, false,
        "登录完成后，较晚返回的旧 /users/me 不得隐藏管理员入口");

    const logoutHarness = createHarness();
    await logoutHarness.context.session.logout();
    logoutHarness.resolveMe(response(200, {
        success: true,
        data: { id: 1, email: "admin@example.com", role: "ADMIN" }
    }));
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(logoutHarness.adminLink.hidden, true,
        "退出完成后，较晚返回的旧 /users/me 不得恢复管理员入口");

    console.log("Session navigation race tests passed.");
})().catch(error => {
    console.error(error);
    process.exit(1);
});
