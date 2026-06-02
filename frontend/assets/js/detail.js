const params = new URLSearchParams(location.search);
const itemId = params.get("id");
const itemDetail = document.querySelector("#itemDetail");
const messageList = document.querySelector("#messageList");
const messageForm = document.querySelector("#messageForm");

async function loadDetail() {
    const result = await request(`/items/${itemId}`);
    const item = result.data;
    if (!item) {
        itemDetail.innerHTML = "<p>物品不存在</p>";
        return;
    }

    itemDetail.innerHTML = `
        <img class="detail-image" src="${item.imageUrl || "assets/images/placeholder.svg"}" alt="${item.title}">
        <h1>${item.title}</h1>
        <p><span class="tag">${item.category}</span></p>
        <p class="price">￥${Number(item.price).toFixed(2)}</p>
        <p>${item.description || ""}</p>
        <button id="createOrder">创建订单</button>
    `;

    document.querySelector("#createOrder").addEventListener("click", async () => {
        const order = await request("/orders", {
            method: "POST",
            body: JSON.stringify({ itemId: item.id, buyerId: 2, sellerId: item.sellerId })
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
    const data = formToJson(messageForm);
    data.itemId = Number(itemId);
    data.senderId = Number(data.senderId);
    data.receiverId = Number(data.receiverId);
    await request("/messages", {
        method: "POST",
        body: JSON.stringify(data)
    });
    messageForm.content.value = "";
    loadMessages();
});

loadDetail();
loadMessages();

