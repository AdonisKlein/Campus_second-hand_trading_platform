const currentUserInfo = document.querySelector("#currentUserInfo");
const orderList = document.querySelector("#orderList");

function escapeHtml(value) {
    return String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
}

function renderOrder(order) {
    return `
        <div class="table-row">
            <span>订单 #${order.id}</span>
            <span>物品：${escapeHtml(order.itemTitle || order.itemId)}</span>
            <span>价格：${order.itemPrice ? `￥${order.itemPrice}` : "-"}</span>
            <span>买家：${escapeHtml(order.buyerNickname || order.buyerId)}</span>
            <span>卖家：${escapeHtml(order.sellerNickname || order.sellerId)}</span>
            <span>状态：${escapeHtml(order.status)}</span>
            <span>
                <button onclick="updateOrderStatus(${order.id}, 'CONFIRMED')">确认</button>
                <button onclick="updateOrderStatus(${order.id}, 'COMPLETED')">完成</button>
                <button onclick="updateOrderStatus(${order.id}, 'CANCELLED')">取消</button>
            </span>
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

async function updateOrderStatus(orderId, status) {
    const result = await request(`/orders/${orderId}/status`, {
        method: "PUT",
        body: JSON.stringify({ status })
    });

    alert(result.success ? "订单状态更新成功" : result.message);
    loadOrders();
}

window.updateOrderStatus = updateOrderStatus;
loadOrders();
