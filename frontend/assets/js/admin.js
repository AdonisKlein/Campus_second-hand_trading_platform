const adminGate = document.querySelector("#adminGate");
const adminPanel = document.querySelector("#adminPanel");
const adminUserInfo = document.querySelector("#adminUserInfo");
const adminUserList = document.querySelector("#adminUserList");
const adminMessageList = document.querySelector("#adminMessageList");
const adminItemList = document.querySelector("#adminItemList");
const userCount = document.querySelector("#userCount");
const messageCount = document.querySelector("#messageCount");
const adminItemCount = document.querySelector("#adminItemCount");
const adminStatUsers = document.querySelector("#adminStatUsers");
const adminStatItems = document.querySelector("#adminStatItems");
const adminStatMessages = document.querySelector("#adminStatMessages");
const adminStatRemoved = document.querySelector("#adminStatRemoved");
const adminReportList = document.querySelector("#adminReportList");
const adminReportCount = document.querySelector("#adminReportCount");
const adminReportStatus = document.querySelector("#adminReportStatus");
const adminReportMessage = document.querySelector("#adminReportMessage");

let currentUser = null;

function escapeHtml(value) {
    return String(value || "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function formatTime(value) {
    if (!value) return "";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return String(value).replace("T", " ").slice(0, 16);
    }
    const pad = number => String(number).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function requireAdmin() {
    if (!currentUser || !currentUser.id || currentUser.role !== "ADMIN") {
        adminGate.style.display = "grid";
        adminPanel.style.display = "none";
        return false;
    }

    adminGate.style.display = "none";
    adminPanel.style.display = "grid";
    adminUserInfo.textContent = `${currentUser.nickname || currentUser.username || "管理员"}`;
    return true;
}

async function adminRequest(path, options = {}) {
    return request(`/admin${path}`, options);
}

async function loadUsers() {
    const result = await adminRequest("/users");
    const users = result.data || [];
    userCount.textContent = `${users.length} 个用户`;
    adminStatUsers.textContent = users.length;
    adminUserList.innerHTML = users.length ? users.map(user => {
        const disabled = user.status === "DISABLED";
        return `
            <div class="table-row admin-row">
                <div>
                    <strong>${escapeHtml(user.nickname || user.username)}</strong>
                    <p>${escapeHtml(user.username)}</p>
                </div>
                <div>${escapeHtml(user.email || "未填写邮箱")}</div>
                <div><span class="tag">${disabled ? "已禁用" : "正常"}</span></div>
                <div class="admin-row-actions">
                    <button type="button" class="${disabled ? "" : "secondary"}" data-action="user-status" data-user-id="${user.id}" data-status="${disabled ? "ACTIVE" : "DISABLED"}">
                        ${disabled ? "恢复" : "禁用"}
                    </button>
                </div>
            </div>
        `;
    }).join("") : "<p>暂无普通用户</p>";
}

async function loadMessages() {
    const result = await adminRequest("/messages");
    const messages = result.data || [];
    messageCount.textContent = `${messages.length} 条留言`;
    adminStatMessages.textContent = messages.length;
    adminMessageList.innerHTML = messages.length ? messages.map(message => `
        <div class="table-row admin-row">
            <div>
                <strong>${escapeHtml(message.senderNickname || `用户 ${message.senderId}`)}</strong>
                <p>${escapeHtml(formatTime(message.createdAt))}</p>
            </div>
            <div>商品 #${message.itemId}</div>
            <div>${escapeHtml(message.content)}</div>
            <div class="admin-row-actions">
                <button type="button" class="secondary danger" data-action="delete-message" data-message-id="${message.id}">删除</button>
            </div>
        </div>
    `).join("") : "<p>暂无留言</p>";
}

async function loadItems() {
    const result = await adminRequest("/items");
    const items = result.data || [];
    adminItemCount.textContent = `${items.length} 件商品`;
    adminStatItems.textContent = items.length;
    adminStatRemoved.textContent = items.filter(item => item.moderationStatus === "REMOVED").length;
    adminItemList.innerHTML = items.length ? items.map(item => {
        const removed = item.moderationStatus === "REMOVED";
        return `
            <div class="table-row admin-row">
                <div>
                    <strong>${escapeHtml(item.title)}</strong>
                    <p>${escapeHtml(item.category)} · ￥${Number(item.price).toFixed(2)}</p>
                </div>
                <div>卖家 #${item.sellerId}</div>
                <div><span class="tag">${removed ? "已下架" : item.status}</span></div>
                <div class="admin-row-actions">
                    <button type="button" class="${removed ? "" : "secondary"}" data-action="item-status" data-item-id="${item.id}" data-status="${removed ? "VISIBLE" : "REMOVED"}">
                        ${removed ? "重新上架" : "下架"}
                    </button>
                </div>
            </div>
        `;
    }).join("") : "<p>暂无商品</p>";
}

function adminReportLabel(value) { return ({ FRAUD:"疑似诈骗", PROHIBITED_CONTENT:"违规内容", HARASSMENT:"骚扰行为", SPAM:"垃圾广告", OTHER:"其他问题" })[value] || value; }
function adminTargetLabel(value) { return ({ ITEM:"商品", MESSAGE:"留言", USER:"用户" })[value] || value; }
function adminStatusLabel(value) { return ({ OPEN:"待处理", RESOLVED:"已确认", DISMISSED:"已驳回" })[value] || value; }
function adminActionStateLabel(value) { return ({ NONE:"无需执行", PENDING:"治理处理中", APPLIED:"治理已生效", FAILED:"治理执行失败" })[value] || value; }
function expectedAction(type) { return ({ ITEM:"REMOVE_ITEM", MESSAGE:"REMOVE_MESSAGE", USER:"DISABLE_USER" })[type]; }

async function loadReports() {
    const query = adminReportStatus.value ? `?status=${adminReportStatus.value}` : "";
    const result = await adminRequest(`/reports${query}`);
    const reports = result.data?.reports || [];
    adminReportCount.textContent = `${reports.length} 条举报`;
    adminReportList.innerHTML = reports.length ? reports.map(report => `
        <article class="report-card admin-report-card" data-report-id="${report.id}">
            <div class="report-card-top"><span class="status-badge ${report.status === "OPEN" ? "pending" : report.status === "RESOLVED" ? "completed" : "cancelled"}">${adminStatusLabel(report.status)}</span><time>${escapeHtml(formatTime(report.createdAt))}</time></div>
            <h3>${adminTargetLabel(report.targetType)} #${report.targetId}：${escapeHtml(report.targetSummary)}</h3>
            <p><strong>${escapeHtml(report.reporterName)}</strong> 举报为“${adminReportLabel(report.reasonCode)}”</p><p>${escapeHtml(report.description)}</p>
            ${report.actionState && report.actionState !== "NONE" ? `<div class="report-resolution"><strong>${adminActionStateLabel(report.actionState)}</strong>${report.actionError ? `<p>${escapeHtml(report.actionError)}</p>` : ""}</div>` : ""}
            ${report.evidenceSnapshot
                ? `<section class="report-evidence"><strong>举报关联聊天证据</strong><pre>${escapeHtml(report.evidenceSnapshot)}</pre></section>`
                : report.targetType === "USER" ? '<p class="report-evidence-missing">该举报未关联聊天会话，无法展示聊天证据。</p>' : ""}
            ${report.resolutionNote ? `<div class="report-resolution"><strong>处理说明</strong><p>${escapeHtml(report.resolutionNote)}</p></div>` : ""}
            ${report.status === "OPEN" ? `<div class="admin-row-actions"><button type="button" data-action="resolve-report" data-report-id="${report.id}" data-target-type="${report.targetType}">确认并治理</button><button type="button" class="secondary" data-action="dismiss-report" data-report-id="${report.id}">驳回举报</button></div>` : ""}
            ${report.actionState === "FAILED" && report.status === "RESOLVED" ? `<div class="admin-row-actions"><button type="button" class="secondary" data-action="retry-report" data-report-id="${report.id}" data-report-action="${report.decisionAction || ""}" data-report-note="${escapeHtml(report.resolutionNote || "")}">重试治理</button></div>` : ""}
        </article>`).join("") : '<p class="empty-state">当前筛选下没有举报。</p>';
}

function switchTab(tab) {
    document.querySelectorAll(".admin-tabs button").forEach(button => {
        const active = button.dataset.adminTab === tab;
        button.classList.toggle("active", active);
        button.classList.toggle("secondary", !active);
        button.setAttribute("aria-selected", String(active));
        button.tabIndex = active ? 0 : -1;
    });
    document.querySelectorAll(".admin-section").forEach(section => {
        section.style.display = section.dataset.adminSection === tab ? "" : "none";
    });
}

document.querySelectorAll(".admin-tabs button").forEach(button => {
    button.addEventListener("click", () => switchTab(button.dataset.adminTab));
});

document.querySelector(".admin-tabs")?.addEventListener("keydown", event => {
    const tabs = [...document.querySelectorAll(".admin-tabs button")];
    const currentIndex = tabs.indexOf(document.activeElement);
    if (currentIndex < 0) return;
    let nextIndex = currentIndex;
    if (event.key === "ArrowRight") nextIndex = (currentIndex + 1) % tabs.length;
    else if (event.key === "ArrowLeft") nextIndex = (currentIndex - 1 + tabs.length) % tabs.length;
    else if (event.key === "Home") nextIndex = 0;
    else if (event.key === "End") nextIndex = tabs.length - 1;
    else return;
    event.preventDefault();
    switchTab(tabs[nextIndex].dataset.adminTab);
    tabs[nextIndex].focus();
});

adminUserList.addEventListener("click", async event => {
    const button = event.target.closest("button[data-action='user-status']");
    if (!button) return;

    const result = await request(`/admin/users/${button.dataset.userId}/status`, {
        method: "PUT",
        body: JSON.stringify({ status: button.dataset.status })
    });
    if (!result.success) {
        alert(result.message);
        return;
    }
    loadUsers();
});

adminMessageList.addEventListener("click", async event => {
    const button = event.target.closest("button[data-action='delete-message']");
    if (!button) return;
    if (!confirm("确定删除这条留言吗？")) return;

    const result = await request(`/admin/messages/${button.dataset.messageId}`, {
        method: "DELETE"
    });
    if (!result.success) {
        alert(result.message);
        return;
    }
    loadMessages();
});

adminItemList.addEventListener("click", async event => {
    const button = event.target.closest("button[data-action='item-status']");
    if (!button) return;

    const result = await request(`/admin/items/${button.dataset.itemId}/status`, {
        method: "PUT",
        body: JSON.stringify({ status: button.dataset.status })
    });
    if (!result.success) {
        alert(result.message);
        return;
    }
    loadItems();
});

adminReportStatus.addEventListener("change", loadReports);
adminReportList.addEventListener("click", async event => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const retrying = button.dataset.action === "retry-report";
    const resolving = button.dataset.action === "resolve-report" || retrying;
    const note = retrying ? button.dataset.reportNote : prompt(resolving ? "请填写确认举报及治理说明" : "请填写驳回原因");
    if (!note || note.trim().length < 2) { adminReportMessage.textContent = "处理说明至少需要 2 个字"; return; }
    button.disabled = true;
    const result = await adminRequest(`/reports/${button.dataset.reportId}`, { method:"PUT", body:JSON.stringify({
        status: resolving ? "RESOLVED" : "DISMISSED", action: retrying ? button.dataset.reportAction : resolving ? expectedAction(button.dataset.targetType) : "NONE", note: note.trim()
    }) });
    button.disabled = false;
    const state = result.data?.actionState;
    adminReportMessage.textContent = result.success
        ? (state === "PENDING" ? "举报已确认，治理措施正在处理中" : state === "FAILED" ? "举报已确认，但治理措施执行失败，可重试" : "举报处理完成，治理措施已经生效")
        : (result.message || "处理失败");
    if (result.success) { loadReports(); loadUsers(); loadMessages(); loadItems(); }
});

(async function initAdmin() {
    currentUser = await session.current();
    if (requireAdmin()) {
        loadUsers();
        loadMessages();
        loadItems();
        loadReports();
    }
})();
