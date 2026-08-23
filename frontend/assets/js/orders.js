const currentUserInfo = document.querySelector("#currentUserInfo");
const orderList = document.querySelector("#orderList");

function escapeHtml(value) {
    return String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
}

function renderOrder(order) {
    const actionLabels = { ACCEPT: "接受订单", COMPLETE: "确认收货", CANCEL: "取消订单" };
    const statusLabels = {
        PENDING_SELLER_CONFIRMATION: "待卖家确认",
        WAITING_HANDOVER: "待当面交易",
        COMPLETED: "交易完成",
        CANCELLED: "已取消",
        EXPIRED: "预留已过期"
    };
    const actions = (order.allowedActions || []).map(action =>
        `<button type="button" data-order-id="${order.id}" data-order-action="${action}">${actionLabels[action]}</button>`
    ).join("");
    return `
        <div class="table-row">
            <span>订单 #${order.id}</span>
            <span>物品：${escapeHtml(order.itemTitle || order.itemId)}</span>
            <span>价格：${order.itemPrice ? `￥${order.itemPrice}` : "-"}</span>
            <span>买家：${escapeHtml(order.buyerNickname || order.buyerId)}</span>
            <span>卖家：${escapeHtml(order.sellerNickname || order.sellerId)}</span>
            <span>状态：${escapeHtml(statusLabels[order.status] || order.status)}</span>
            <span>${actions || "暂无可用操作"}</span>
        </div>
    `;
}

async function loadOrders() {
    const currentUser = await session.current();
    if (!currentUser || !currentUser.id) {
        if (currentUserInfo) {
            currentUserInfo.textContent = "";
        }
        orderList.innerHTML = "<p>请先登录以查看订单</p>";
        setTimeout(() => {
            location.href = "profile.html";
        }, 800);
        return;
    }

    if (currentUserInfo) {
        currentUserInfo.textContent =
            `当前用户：${currentUser.nickname || currentUser.username || currentUser.id}`;
    }

    const result = await request("/orders");
    const orders = result.data || [];
    orderList.innerHTML = orders.length
        ? orders.map(renderOrder).join("")
        : "<p>暂无订单</p>";
}

async function performOrderAction(orderId, action) {
    const result = await request(`/orders/${orderId}/actions`, {
        method: "POST",
        body: JSON.stringify({ action })
    });

    alert(result.success ? "订单状态更新成功" : result.message);
    loadOrders();
}

orderList.addEventListener("click", event => {
    const button = event.target.closest("button[data-order-action]");
    if (!button || !orderList.contains(button)) return;
    performOrderAction(Number(button.dataset.orderId), button.dataset.orderAction);
});

loadOrders();
