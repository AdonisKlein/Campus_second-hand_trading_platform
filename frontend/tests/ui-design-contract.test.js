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
for (const selector of ["market-shell", "category-sidebar", "campaign-banner", "category-chips"]) {
    if (!home.includes(`class=\"${selector}`) && !home.includes(` ${selector}`)) {
        failures.push(`index.html: 缺少设计预览结构 .${selector}`);
    }
}

const admin = fs.readFileSync(path.join(frontend, "admin.html"), "utf8");
if (!admin.includes("admin-dashboard-stats")) failures.push("admin.html: 缺少控制台统计区");

const api = fs.readFileSync(path.join(frontend, "assets/js/api.js"), "utf8");
if (!api.includes("hydrateRoleNavigation")) failures.push("api.js: 缺少统一角色导航 hydration");

if (failures.length) {
    console.error(failures.join("\n"));
    process.exit(1);
}
console.log(`UI design contract passed for ${htmlFiles.length} pages.`);
