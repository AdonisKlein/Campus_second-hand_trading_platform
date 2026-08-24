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
    const statusClass = {
        PENDING_SELLER_CONFIRMATION: "pending",
        WAITING_HANDOVER: "waiting",
        COMPLETED: "completed",
        CANCELLED: "cancelled",
        EXPIRED: "expired"
    }[order.status] || "";
    const statusHints = {
        PENDING_SELLER_CONFIRMATION: "等待卖家确认订单，请留意状态变化",
        WAITING_HANDOVER: "双方可约定校内地点，当面验货交易",
        COMPLETED: "这笔交易已经顺利完成",
        CANCELLED: "订单已取消，商品会重新进入可售状态",
        EXPIRED: "预留时间已结束，商品已释放"
    };
    return `
        <div class="table-row order-card">
            <div class="order-card__head">
                <div><small>ORDER NO.</small><strong>#${order.id}</strong></div>
                <span class="status-badge ${statusClass}">${escapeHtml(statusLabels[order.status] || order.status)}</span>
            </div>
            <div class="order-card__body">
                <div class="order-item-name"><small>商品</small><strong>${escapeHtml(order.itemTitle || order.itemId)}</strong><span>${escapeHtml(statusHints[order.status] || "")}</span></div>
                <div><small>成交价</small><strong class="price">${order.itemPrice != null ? `￥${order.itemPrice}` : "-"}</strong></div>
                <div><small>买家</small><span>${escapeHtml(order.buyerNickname || order.buyerId)}</span></div>
                <div><small>卖家</small><span>${escapeHtml(order.sellerNickname || order.sellerId)}</span></div>
            </div>
            <div class="order-card__actions">${actions || '<span class="order-card__empty-action">当前没有需要处理的操作</span>'}</div>
        </div>
    `;
}

async function loadOrders() {
    const currentUser = await requireAuthenticatedUser({ message: "登录后才能查看和处理订单，是否前往登录？", returnTo: "orders.html" });
    if (!currentUser) {
        if (currentUserInfo) {
            currentUserInfo.textContent = "";
        }
        orderList.innerHTML = "<p>请先登录以查看订单</p>";
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
        : '<p class="empty-state">暂无订单，去首页看看校园好物吧。</p>';
}

async function performOrderAction(orderId, action, button) {
    button.disabled = true;
    const result = await request(`/orders/${orderId}/actions`, {
        method: "POST",
        body: JSON.stringify({ action })
    });

    alert(result.success ? "订单状态更新成功" : result.message);
    if (result.success) loadOrders();
    else button.disabled = false;
}

orderList.addEventListener("click", event => {
    const button = event.target.closest("button[data-order-action]");
    if (!button || !orderList.contains(button)) return;
    performOrderAction(Number(button.dataset.orderId), button.dataset.orderAction, button);
});

loadOrders();
