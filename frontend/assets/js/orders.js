const orderList = document.querySelector("#orderList");

function renderOrder(order) {
    return `
        <div class="table-row">
            <span>订单 #${order.id}</span>
            <span>物品 ${order.itemId}</span>
            <span>买家 ${order.buyerId}</span>
            <span>卖家 ${order.sellerId}</span>
            <span>${order.status}</span>
        </div>
    `;
}

async function loadOrders() {
    const currentUser = getCurrentUser();
    if (!currentUser) {
        orderList.innerHTML = '<p>请先登录以查看订单</p>';
        setTimeout(() => { location.href = 'profile.html'; }, 800);
        return;
    }
    const result = await request(`/orders?userId=${currentUser.id}`);
    const orders = result.data || [];
    orderList.innerHTML = orders.length
        ? orders.map(renderOrder).join("")
        : "<p>暂无订单</p>";
}

loadOrders();
