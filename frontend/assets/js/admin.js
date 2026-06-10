const adminGate = document.querySelector("#adminGate");
const adminPanel = document.querySelector("#adminPanel");
const adminUserInfo = document.querySelector("#adminUserInfo");
const adminUserList = document.querySelector("#adminUserList");
const adminMessageList = document.querySelector("#adminMessageList");
const adminItemList = document.querySelector("#adminItemList");
const userCount = document.querySelector("#userCount");
const messageCount = document.querySelector("#messageCount");
const adminItemCount = document.querySelector("#adminItemCount");

const currentUser = getCurrentUser();

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
    const separator = path.includes("?") ? "&" : "?";
    return request(`/admin${path}${separator}adminId=${encodeURIComponent(currentUser.id)}`, options);
}

async function loadUsers() {
    const result = await adminRequest("/users");
    const users = result.data || [];
    userCount.textContent = `${users.length} 个用户`;
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
    adminItemList.innerHTML = items.length ? items.map(item => {
        const removed = item.status === "REMOVED";
        return `
            <div class="table-row admin-row">
                <div>
                    <strong>${escapeHtml(item.title)}</strong>
                    <p>${escapeHtml(item.category)} · ￥${Number(item.price).toFixed(2)}</p>
                </div>
                <div>卖家 #${item.sellerId}</div>
                <div><span class="tag">${removed ? "已下架" : item.status}</span></div>
                <div class="admin-row-actions">
                    <button type="button" class="${removed ? "" : "secondary"}" data-action="item-status" data-item-id="${item.id}" data-status="${removed ? "ON_SALE" : "REMOVED"}">
                        ${removed ? "重新上架" : "下架"}
                    </button>
                </div>
            </div>
        `;
    }).join("") : "<p>暂无商品</p>";
}

function switchTab(tab) {
    document.querySelectorAll(".admin-tabs button").forEach(button => {
        const active = button.dataset.adminTab === tab;
        button.classList.toggle("active", active);
        button.classList.toggle("secondary", !active);
    });
    document.querySelectorAll(".admin-section").forEach(section => {
        section.style.display = section.dataset.adminSection === tab ? "" : "none";
    });
}

document.querySelectorAll(".admin-tabs button").forEach(button => {
    button.addEventListener("click", () => switchTab(button.dataset.adminTab));
});

adminUserList.addEventListener("click", async event => {
    const button = event.target.closest("button[data-action='user-status']");
    if (!button) return;

    const result = await request(`/admin/users/${button.dataset.userId}/status`, {
        method: "PUT",
        body: JSON.stringify({ adminId: Number(currentUser.id), status: button.dataset.status })
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
        method: "DELETE",
        body: JSON.stringify({ adminId: Number(currentUser.id) })
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
        body: JSON.stringify({ adminId: Number(currentUser.id), status: button.dataset.status })
    });
    if (!result.success) {
        alert(result.message);
        return;
    }
    loadItems();
});

if (requireAdmin()) {
    loadUsers();
    loadMessages();
    loadItems();
}
