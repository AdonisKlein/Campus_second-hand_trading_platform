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
for (const selector of ["student-market-header", "market-search", "discovery-chips", "search-filter-panel"]) {
    if (!home.includes(`class=\"${selector}`) && !home.includes(` ${selector}`)) {
        failures.push(`index.html: 缺少设计预览结构 .${selector}`);
    }
}
if (home.includes("category-sidebar")) failures.push("index.html: 学生端不应使用商品分类侧边栏");
if (!home.includes("data-search-scope")) failures.push("index.html: 搜索后必须能切换商品/用户");

const applicationMarkup = htmlFiles.map(file => fs.readFileSync(path.join(frontend, file), "utf8")).join("\n")
    + fs.readdirSync(path.join(frontend, "assets/js")).filter(file => file.endsWith(".js"))
        .map(file => fs.readFileSync(path.join(frontend, "assets/js", file), "utf8")).join("\n");
for (const promotionalBlock of ["campaign-banner", "auth-intro", "editor-guide", "order-page-intro", "trade-process-card", "section-kicker", "eyebrow"]) {
    if (applicationMarkup.includes(promotionalBlock)) failures.push(`实际业务页面不得包含宣传或流程介绍块 ${promotionalBlock}`);
}

for (const file of htmlFiles) {
    const html = fs.readFileSync(path.join(frontend, file), "utf8");
    const protectedLinks = html.match(/<a[^>]+href="(?:publish|orders|my-items|reports|messages)\.html[^>]*>/g) || [];
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
const detailHtml = fs.readFileSync(path.join(frontend, "detail.html"), "utf8");
if (!detail.includes('openReportDialog("ITEM"') || !detail.includes('openReportDialog("MESSAGE"')) {
    failures.push("detail.js: 商品和留言举报入口必须同时接入统一 module");
}
for (const selector of ["product-detail-page", "detail-content-grid", "mobileProductActions"]) {
    if (!detailHtml.includes(selector)) failures.push(`detail.html: 第十二轮商品详情缺少 ${selector}`);
}
for (const contract of ["availableActions", "purchaseRequest", "sellerItems", "seller-profile-card", "product-gallery"]) {
    if (!detail.includes(contract)) failures.push(`detail.js: 商品详情投影或布局缺少 ${contract}`);
}
const main = fs.readFileSync(path.join(frontend, "assets/js/main.js"), "utf8");
if (!main.includes('openReportDialog("USER"')) failures.push("main.js: 用户搜索结果缺少举报用户入口");
if (!admin.includes("adminReportsPanel")) failures.push("admin.html: 缺少管理员举报队列");

const chat = fs.readFileSync(path.join(frontend, "messages.html"), "utf8");
const chatJs = fs.readFileSync(path.join(frontend, "assets/js/chat.js"), "utf8");
const styles = fs.readFileSync(path.join(frontend, "assets/css/styles.css"), "utf8");
for (const selector of ["chat-shell", "conversationList", "chatMessages", "chatForm"]) {
    if (!chat.includes(selector)) failures.push(`messages.html: 缺少私聊结构 ${selector}`);
}
for (const selector of ["conversationSearch", "unreadFirst", "chatContext", "chatItemState", "historyStatus"]) {
    if (!chat.includes(selector)) failures.push(`messages.html: 第十五轮私聊工作台缺少 ${selector}`);
}
for (const contract of ["itemPrice", "itemStatus", "retryMessage", "preserveReadingPosition", "loadMoreConversations"]) {
    if (!chatJs.includes(contract)) failures.push(`chat.js: 第十五轮私聊投影或交互缺少 ${contract}`);
}
if (!/\.conversation-list\s*\{[^}]*min-height:0[^}]*overflow-y:auto/s.test(styles)) {
    failures.push("styles.css: 左侧会话列表必须在固定工作区内独立滚动");
}
if (!/\.chat-messages\s*\{[^}]*min-height:0[^}]*overflow-y:auto/s.test(styles)) {
    failures.push("styles.css: 聊天消息必须在固定工作区内独立滚动");
}

const ordersHtml = fs.readFileSync(path.join(frontend, "orders.html"), "utf8");
const ordersJs = fs.readFileSync(path.join(frontend, "assets/js/orders.js"), "utf8");
for (const contract of ["order-workspace", "order-perspective-tabs", "order-stage-tabs", "orderDetailDialog", "orderActionDialog"]) {
    if (!ordersHtml.includes(contract)) failures.push(`orders.html: 第十三轮订单工作台缺少 ${contract}`);
}
for (const contract of ["/orders/desk", "data-order-action", "data-chat-order", 'openReportDialog("USER"', "timeline"]) {
    if (!ordersJs.includes(contract)) failures.push(`orders.js: 订单工作台接口或交互缺少 ${contract}`);
}
if (ordersJs.includes("alert(")) failures.push("orders.js: 订单操作不得使用浏览器 alert");
if (!chatJs.includes("/chat/conversations") || !chatJs.includes("preserveReadingPosition")) {
    failures.push("chat.js: 缺少私聊 API 或轮询阅读位置保护");
}
for (const file of htmlFiles.filter(name => !["messages.html"].includes(name))) {
    const html = fs.readFileSync(path.join(frontend, file), "utf8");
    if (!html.includes('href="messages.html"')) failures.push(`${file}: 学生端公共导航缺少私聊入口`);
}

if (failures.length) {
    console.error(failures.join("\n"));
    process.exit(1);
}
console.log(`UI design contract passed for ${htmlFiles.length} pages.`);
