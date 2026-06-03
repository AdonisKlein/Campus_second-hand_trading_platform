const params = new URLSearchParams(location.search);
const itemId = params.get("id");
const itemDetail = document.querySelector("#itemDetail");
const messageList = document.querySelector("#messageList");
const messageForm = document.querySelector("#messageForm");
let currentItem = null;

async function loadDetail() {
    const result = await request(`/items/${itemId}`);
    const item = result.data;
    if (!item) {
        itemDetail.innerHTML = "<p>物品不存在</p>";
        return;
    }
    currentItem = item;

    itemDetail.innerHTML = `
        <img class="detail-image" src="${item.imageUrl || "assets/images/placeholder.svg"}" alt="${item.title}">
        <h1>${item.title}</h1>
        <p><span class="tag">${item.category}</span></p>
        <p class="price">￥${Number(item.price).toFixed(2)}</p>
        <p>${item.description || ""}</p>
        <button id="createOrder">创建订单</button>
    `;

    document.querySelector("#createOrder").addEventListener("click", async () => {
        const currentUser = getCurrentUser();
        if (!currentUser) {
            alert('请先登录后下单');
            location.href = 'profile.html';
            return;
        }
        const order = await request("/orders", {
            method: "POST",
            body: JSON.stringify({ itemId: item.id, buyerId: Number(currentUser.id), sellerId: item.sellerId })
        });
        alert(order.success ? "订单创建成功" : order.message);
    });
}

async function loadMessages() {
    const result = await request(`/messages/item/${itemId}`);
    const messages = result.data || [];
    messageList.innerHTML = messages.map(message => `
        <div class="message-item">
            <strong>用户 ${message.senderId}</strong>
            <p>${message.content}</p>
        </div>
    `).join("");
}

messageForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const currentUser = getCurrentUser();
    if (!currentUser) {
        alert('请先登录后留言');
        location.href = 'profile.html';
        return;
    }
    if (!currentItem) {
        alert('物品未加载完成');
        return;
    }
    const data = formToJson(messageForm);
    data.itemId = Number(itemId);
    data.senderId = Number(currentUser.id);
    data.receiverId = Number(currentItem.sellerId);
    await request("/messages", {
        method: "POST",
        body: JSON.stringify(data)
    });
    messageForm.content.value = "";
    loadMessages();
});

loadDetail();
loadMessages();
