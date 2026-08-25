const orderUi = { perspective: "BUYING", stage: "ALL", currentUser: null, desk: null, pendingAction: null };
const orderGroups = document.querySelector("#orderGroups");
const orderSummary = document.querySelector("#orderSummary");
const orderFeedback = document.querySelector("#orderFeedback");
const detailDialog = document.querySelector("#orderDetailDialog");
const actionDialog = document.querySelector("#orderActionDialog");

function h(value) { return String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;"); }
function money(value) { return value == null ? "-" : `￥${Number(value).toFixed(2)}`; }
function dateTime(value) {
    if (!value) return "-";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "-" : date.toLocaleString("zh-CN", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" });
}
function activity(value) {
    if (!value) return "近期活跃";
    const minutes = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 60000));
    if (minutes < 5) return "刚刚活跃";
    if (minutes < 60) return `${minutes} 分钟前活跃`;
    if (minutes < 1440) return `${Math.floor(minutes / 60)} 小时前活跃`;
    return `${Math.floor(minutes / 1440)} 天前活跃`;
}

const statusCopy = {
    PURCHASE_REQUESTED: ["等待卖家回应", "request"], WAITING_HANDOVER: ["待当面交易", "handover"],
    COMPLETED: ["交易完成", "complete"], CANCELLED: ["已取消", "closed"],
    DECLINED: ["未被选中", "closed"], EXPIRED: ["已过期", "closed"]
};
const actionCopy = {
    ACCEPT: ["选定该买家", "确认选定这位买家？", "选定后商品才会预留，其他待回应意向会自动关闭。请随后私聊对方约定校内公共地点。"],
    DECLINE: ["暂不接受", "不接受这次购买意向？", "这位买家会看到卖家暂未接受，本次意向将结束。"],
    COMPLETE: ["确认已取货", "确认已经当面取货？", "确认后交易完成，商品会标记为已售出。请确保你已当面验货并拿到商品。"],
    CANCEL: ["取消交易", "确认取消这次交易？", "取消后当前意向或预留将结束；已经预留的商品会重新公开在售。"]
};

function countdownMarkup(entry) {
    if (!entry.expiresAt || !["PURCHASE_REQUESTED", "WAITING_HANDOVER"].includes(entry.status)) return "";
    return `<span class="order-countdown" data-expires-at="${h(entry.expiresAt)}">计算剩余时间…</span>`;
}
function buyerProfile(entry) {
    const person = entry.counterparty || {};
    return `<div class="order-buyer-profile"><span class="order-avatar">${h((person.nickname || "同学").slice(0, 1))}</span>
        <div><strong>${h(person.nickname || entry.buyerNickname)}</strong><small>${h(person.campusRegion || "校内自取区域待沟通")} · ${h(activity(person.lastActiveAt))}</small></div>
        <div class="order-credit"><span>${person.creditScore ?? "-"}</span><small>校园信用</small></div></div>`;
}
function timelinePreview(entry) {
    return `<ol class="order-mini-timeline">${(entry.timeline || []).map(step => `<li class="${h(step.state.toLowerCase())}"><i></i><span>${h(step.label)}</span></li>`).join("")}</ol>`;
}
function actionButtons(entry) {
    const primary = (entry.allowedActions || []).map(action => {
        const label = actionCopy[action]?.[0];
        if (!label) return "";
        return `<button type="button" class="${action === "ACCEPT" || action === "COMPLETE" ? "primary" : "quiet"}" data-order-action="${action}" data-order-id="${entry.id}">${h(label)}</button>`;
    }).join("");
    return `<div class="order-record-actions">${primary}
        <button type="button" class="quiet" data-open-order="${entry.id}">查看进度</button>
        <a class="quiet" href="detail.html?id=${entry.itemId}">查看商品</a>
        <button type="button" class="quiet" data-chat-order="${entry.id}">私聊对方</button>
        <button type="button" class="text-action danger" data-report-order="${entry.id}">举报</button></div>`;
}
function orderRecord(entry, selling) {
    const [label, statusClass] = statusCopy[entry.status] || ["处理中", "closed"];
    return `<article class="order-record ${entry.allowedActions?.length ? "needs-action" : ""}" data-order-record="${entry.id}">
        <div class="order-record-top"><div><span class="order-status ${statusClass}">${h(label)}</span>${countdownMarkup(entry)}</div><small>意向 #${entry.id} · ${h(dateTime(entry.createdAt))}</small></div>
        ${selling ? buyerProfile(entry) : `<div class="order-counterparty-line"><span class="order-avatar">${h((entry.counterparty?.nickname || "卖").slice(0, 1))}</span><div><small>卖家</small><strong>${h(entry.counterparty?.nickname || entry.sellerNickname)}</strong><span>${h(entry.counterparty?.campusRegion || "校内区域待沟通")} · 信用 ${entry.counterparty?.creditScore ?? "-"}</span></div></div>`}
        ${entry.closureReason ? `<p class="order-closure">${h(entry.closureReason)}</p>` : timelinePreview(entry)}
        ${actionButtons(entry)}</article>`;
}
function groupCard(group) {
    const selling = orderUi.perspective === "SELLING";
    return `<section class="order-item-group"><header><div><p>${selling ? "商品购买意向" : "商品快照"}</p><h3>${h(group.itemTitle)}</h3></div><div><strong>${h(money(group.itemPrice))}</strong><span>${group.entries.length} 条记录</span></div></header>
        <div class="order-entry-list">${group.entries.map(entry => orderRecord(entry, selling)).join("")}</div></section>`;
}
function renderSummary(summary) {
    orderSummary.innerHTML = `<div class="highlight"><span>${summary.actionable}</span><small>现在需要我处理</small></div><div><span>${summary.requests}</span><small>等待卖家回应</small></div><div><span>${summary.handovers}</span><small>待当面交易</small></div><div><span>${summary.closed}</span><small>已经结束</small></div>`;
    for (const key of ["requests", "handovers", "closed"]) document.querySelector(`[data-stage-count="${key}"]`).textContent = summary[key];
}
function allEntries() { return (orderUi.desk?.groups || []).flatMap(group => group.entries || []); }
function entryById(id) { return allEntries().find(entry => Number(entry.id) === Number(id)); }
function paintDesk() {
    const groups = orderUi.desk?.groups || [];
    renderSummary(orderUi.desk.summary);
    document.querySelector("#orderResultCount").textContent = `${groups.reduce((sum, group) => sum + group.entries.length, 0)} 条记录`;
    document.querySelector("#orderListTitle").textContent = ({ ALL: "全部交易", REQUESTS: "等待卖家回应", HANDOVER: "准备当面交易", CLOSED: "已结束的交易" })[orderUi.stage];
    document.querySelector("#orderListHint").textContent = orderUi.perspective === "SELLING" ? "同一商品的购买意向放在一起，方便比较后再选择买家。" : "按交易进度跟进，操作与关闭原因都清楚保留。";
    orderGroups.innerHTML = groups.length ? groups.map(groupCard).join("") : `<div class="order-empty"><span>◎</span><h3>这里还没有交易记录</h3><p>${orderUi.perspective === "BUYING" ? "去首页看看校园好物，和卖家聊好后再提交购买意向。" : "发布闲置后，买家的购买意向会出现在这里。"}</p><a class="button-link" href="${orderUi.perspective === "BUYING" ? "index.html" : "publish.html"}">${orderUi.perspective === "BUYING" ? "去逛逛" : "发布闲置"}</a></div>`;
    orderGroups.setAttribute("aria-busy", "false");
    updateCountdowns();
}
async function loadDesk() {
    orderGroups.setAttribute("aria-busy", "true"); orderGroups.innerHTML = '<div class="order-loading">正在整理你的交易进度…</div>'; orderFeedback.textContent = "";
    const result = await request(`/orders/desk?perspective=${orderUi.perspective}&stage=${orderUi.stage}`);
    if (!result.success) { orderGroups.innerHTML = `<div class="order-empty"><h3>订单加载失败</h3><p>${h(result.message || "请稍后重试")}</p><button type="button" data-retry-orders>重新加载</button></div>`; orderGroups.setAttribute("aria-busy", "false"); return; }
    orderUi.desk = result.data; paintDesk();
}
function updateCountdowns() {
    document.querySelectorAll("[data-expires-at]").forEach(element => {
        const seconds = Math.max(0, Math.floor((new Date(element.dataset.expiresAt).getTime() - Date.now()) / 1000));
        if (!seconds) { element.textContent = "即将到期"; element.classList.add("urgent"); return; }
        const hours = Math.floor(seconds / 3600), minutes = Math.max(1, Math.floor((seconds % 3600) / 60));
        element.textContent = hours ? `剩余 ${hours} 小时 ${minutes} 分` : `剩余 ${minutes} 分钟`; element.classList.toggle("urgent", seconds < 3600);
    });
}
function showDetails(entry) {
    const [label, statusClass] = statusCopy[entry.status] || ["处理中", "closed"];
    document.querySelector("#orderDetailContent").innerHTML = `<h2 id="orderDetailTitle">${h(entry.itemTitle)}</h2>
        <div class="order-detail-price"><span>交易价格快照</span><strong>${h(money(entry.itemPrice))}</strong></div>
        <div class="order-detail-parties"><div><small>买家</small><strong>${h(entry.buyerNickname)}</strong></div><span>⇄</span><div><small>卖家</small><strong>${h(entry.sellerNickname)}</strong></div></div>
        <div class="order-detail-state"><span class="order-status ${statusClass}">${h(label)}</span><p>${h(entry.closureReason || "交易仍在进行，请按当前步骤操作。")}</p></div>
        <ol class="order-full-timeline">${(entry.timeline || []).map(step => `<li class="${h(step.state.toLowerCase())}"><i></i><div><strong>${h(step.label)}</strong><p>${h(step.hint)}</p>${step.occurredAt ? `<time>${h(dateTime(step.occurredAt))}</time>` : ""}</div></li>`).join("")}</ol>
        <div class="order-detail-tips"><strong>当面交易提醒</strong><p>请在校内公共场所见面，先验货再付款，不要脱离平台沟通可疑转账。</p></div>${actionButtons(entry)}`;
    detailDialog.showModal();
}
function confirmOrderAction(entry, action) {
    const copy = actionCopy[action]; if (!copy || !entry) return;
    orderUi.pendingAction = { entry, action }; document.querySelector("#orderActionTitle").textContent = copy[1]; document.querySelector("#orderActionMessage").textContent = copy[2]; document.querySelector("[data-confirm-action]").textContent = copy[0]; actionDialog.showModal();
}
async function submitOrderAction() {
    const pending = orderUi.pendingAction; if (!pending) return;
    const button = document.querySelector("[data-confirm-action]"); button.disabled = true;
    const result = await request(`/orders/${pending.entry.id}/actions`, { method: "POST", body: JSON.stringify({ action: pending.action }) });
    button.disabled = false; actionDialog.close(); orderUi.pendingAction = null;
    orderFeedback.textContent = result.success ? "交易状态已更新。" : (result.message || "操作失败，请刷新后重试。");
    if (result.success) { if (detailDialog.open) detailDialog.close(); await loadDesk(); }
}
async function openOrderChat(entry) {
    orderFeedback.textContent = "正在打开与对方的私聊…";
    const result = await request("/chat/order-conversations", { method: "POST", body: JSON.stringify({ orderId: entry.id }) });
    if (!result.success) { orderFeedback.textContent = result.message || "暂时无法打开私聊"; return; }
    location.href = `messages.html?conversation=${encodeURIComponent(result.data.id)}`;
}

document.querySelector(".order-perspective-tabs").addEventListener("click", event => { const button = event.target.closest("[data-perspective]"); if (!button || button.dataset.perspective === orderUi.perspective) return; orderUi.perspective = button.dataset.perspective; document.querySelectorAll("[data-perspective]").forEach(item => item.setAttribute("aria-selected", String(item === button))); loadDesk(); });
document.querySelector(".order-stage-tabs").addEventListener("click", event => { const button = event.target.closest("[data-stage]"); if (!button || button.dataset.stage === orderUi.stage) return; orderUi.stage = button.dataset.stage; document.querySelectorAll("[data-stage]").forEach(item => item.setAttribute("aria-selected", String(item === button))); loadDesk(); });
orderGroups.addEventListener("click", async event => {
    if (event.target.closest("[data-retry-orders]")) return loadDesk();
    const action = event.target.closest("[data-order-action]"); if (action) return confirmOrderAction(entryById(action.dataset.orderId), action.dataset.orderAction);
    const detail = event.target.closest("[data-open-order]"); if (detail) return showDetails(entryById(detail.dataset.openOrder));
    const chat = event.target.closest("[data-chat-order]"); if (chat) return openOrderChat(entryById(chat.dataset.chatOrder));
    const report = event.target.closest("[data-report-order]"); if (report) { const entry = entryById(report.dataset.reportOrder); if (await openReportDialog("USER", entry.counterparty.id, entry.counterparty.nickname)) orderFeedback.textContent = "举报已提交，管理员会按规则处理。"; }
});
detailDialog.addEventListener("click", async event => {
    if (event.target.closest("[data-close-detail]")) return detailDialog.close();
    const action = event.target.closest("[data-order-action]"); if (action) return confirmOrderAction(entryById(action.dataset.orderId), action.dataset.orderAction);
    const chat = event.target.closest("[data-chat-order]"); if (chat) return openOrderChat(entryById(chat.dataset.chatOrder));
    const report = event.target.closest("[data-report-order]"); if (report) { const entry = entryById(report.dataset.reportOrder); await openReportDialog("USER", entry.counterparty.id, entry.counterparty.nickname); }
});
document.querySelector("[data-cancel-action]").addEventListener("click", () => actionDialog.close());
actionDialog.querySelector("form").addEventListener("submit", event => { event.preventDefault(); submitOrderAction(); });

(async () => {
    orderUi.currentUser = await requireAuthenticatedUser({ message: "登录后才能查看和处理交易，是否前往登录？", returnTo: "orders.html" });
    if (!orderUi.currentUser) { orderGroups.innerHTML = '<div class="order-empty"><h3>登录后查看交易</h3><p>你的购买意向和卖出记录会安全保存在账号中。</p></div>'; return; }
    document.querySelector("#currentUserInfo").textContent = orderUi.currentUser.nickname || orderUi.currentUser.username;
    await loadDesk(); setInterval(updateCountdowns, 30000);
})();
