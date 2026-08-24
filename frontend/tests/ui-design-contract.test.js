const fs = require("node:fs");
const path = require("node:path");

const frontend = path.resolve(__dirname, "..");
const htmlFiles = fs.readdirSync(frontend).filter(name => name.endsWith(".html"));
const failures = [];

for (const file of htmlFiles) {
    const html = fs.readFileSync(path.join(frontend, file), "utf8");
    const adminLinks = html.match(/<a[^>]+href="admin\.html"[^>]*>/g) || [];
    for (const link of adminLinks) {
        if (!link.includes("data-admin-only") || !link.includes("hidden")) {
            failures.push(`${file}: 管理中心入口必须默认隐藏并标记 data-admin-only`);
        }
    }
}

const home = fs.readFileSync(path.join(frontend, "index.html"), "utf8");
for (const selector of ["student-market-header", "market-search", "campaign-banner", "discovery-chips", "search-filter-panel"]) {
    if (!home.includes(`class=\"${selector}`) && !home.includes(` ${selector}`)) {
        failures.push(`index.html: 缺少设计预览结构 .${selector}`);
    }
}
if (home.includes("category-sidebar")) failures.push("index.html: 学生端不应使用商品分类侧边栏");
if (/<section class="campaign-banner">[\s\S]*?<a class="button-link"/.test(home)) {
    failures.push("index.html: 活动横幅不应复用全宽 button-link");
}
if (!home.includes("data-search-scope")) failures.push("index.html: 搜索后必须能切换商品/用户");

for (const file of htmlFiles) {
    const html = fs.readFileSync(path.join(frontend, file), "utf8");
    const protectedLinks = html.match(/<a[^>]+href="(?:publish|orders|my-items|reports)\.html[^>]*>/g) || [];
    for (const link of protectedLinks) {
        if (!link.includes("data-requires-auth")) failures.push(`${file}: 登录后操作入口缺少 data-requires-auth`);
    }
}

const admin = fs.readFileSync(path.join(frontend, "admin.html"), "utf8");
if (!admin.includes("admin-dashboard-stats")) failures.push("admin.html: 缺少控制台统计区");

const api = fs.readFileSync(path.join(frontend, "assets/js/api.js"), "utf8");
if (!api.includes("hydrateRoleNavigation")) failures.push("api.js: 缺少统一角色导航 hydration");
if (!api.includes("confirmAuthentication")) failures.push("api.js: 缺少统一的页面内登录确认 module");
if (!api.includes("openReportDialog")) failures.push("api.js: 缺少统一举报 dialog module");

const detail = fs.readFileSync(path.join(frontend, "assets/js/detail.js"), "utf8");
if (!detail.includes('openReportDialog("ITEM"') || !detail.includes('openReportDialog("MESSAGE"')) {
    failures.push("detail.js: 商品和留言举报入口必须同时接入统一 module");
}
const main = fs.readFileSync(path.join(frontend, "assets/js/main.js"), "utf8");
if (!main.includes('openReportDialog("USER"')) failures.push("main.js: 用户搜索结果缺少举报用户入口");
if (!admin.includes("adminReportsPanel")) failures.push("admin.html: 缺少管理员举报队列");

if (failures.length) {
    console.error(failures.join("\n"));
    process.exit(1);
}
console.log(`UI design contract passed for ${htmlFiles.length} pages.`);
