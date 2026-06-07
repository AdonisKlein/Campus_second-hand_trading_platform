const currentUserInfo = document.querySelector("#currentUserInfo");
const orderList = document.querySelector("#orderList");

function getCurrentUser() {
    return JSON.parse(localStorage.getItem("user") || "{}");
}

async function loadOrders() {
    const currentUser = getCurrentUser();

    if (!currentUser.id) {
        orderList.innerHTML = "<p>请先到个人中心注册/登录</p>";
        return;
    }

    currentUserInfo.textContent =
        `当前用户：${currentUser.nickname || currentUser.username || currentUser.id}`;

    const result = await request(`/orders?userId=${currentUser.id}`);
    const orders = result.data || [];

    orderList.innerHTML = orders.length
        ? orders.map(renderOrder).join("")
        : "<p>暂无订单</p>";
}

function renderOrder(order) {
    return `
        <div class="table-row">
            <span>订单 #${order.id}</span>
            <span>物品：${order.itemTitle}</span>
            <span>价格：￥${order.itemPrice}</span>
            <span>买家：${order.buyerNickname}</span>
            <span>卖家：${order.sellerNickname}</span>
            <span>状态：${order.status}</span>
            <span>
                <button onclick="updateOrderStatus(${order.id}, 'CONFIRMED')">确认</button>
                <button onclick="updateOrderStatus(${order.id}, 'COMPLETED')">完成</button>
                <button onclick="updateOrderStatus(${order.id}, 'CANCELLED')">取消</button>
            </span>
        </div>
    `;
}

async function updateOrderStatus(orderId, status) {
    const result = await request(`/orders/${orderId}/status`, {
        method: "PUT",
        body: JSON.stringify({ status })
    });

    alert(result.success ? "订单状态更新成功" : result.message);
    loadOrders();
}

loadOrders();