const userIdInput = document.querySelector("#userId");
const orderList = document.querySelector("#orderList");

async function loadOrders() {
    const result = await request(`/orders?userId=${userIdInput.value}`);
    const orders = result.data || [];
    orderList.innerHTML = orders.length
        ? orders.map(renderOrder).join("")
        : "<p>暂无订单</p>";
}

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

userIdInput.addEventListener("change", loadOrders);
loadOrders();

