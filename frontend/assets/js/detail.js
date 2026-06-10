const params = new URLSearchParams(location.search);
const itemId = params.get("id");
const itemDetail = document.querySelector("#itemDetail");
const messageList = document.querySelector("#messageList");
const messageForm = document.querySelector("#messageForm");
let currentItem = null;

function escapeHtml(value) {
    return String(value || "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function createMessageEditForm(content) {
    const form = document.createElement("form");
    form.className = "message-edit-form";
    form.innerHTML = `
        <textarea name="content" rows="3" maxlength="500" required>${escapeHtml(content)}</textarea>
        <div class="message-edit-actions">
            <button type="submit">保存</button>
            <button class="secondary" type="button" data-action="cancel">取消</button>
        </div>
    `;
    return form;
}

function formatMessageTime(value) {
    if (!value) {
        return "";
    }

    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return String(value).replace("T", " ").slice(0, 16);
    }

    const pad = number => String(number).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

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
        if (!currentUser || !currentUser.id) {
            alert("请先登录后下单");
            location.href = "profile.html";
            return;
        }

        const order = await request("/orders", {
            method: "POST",
            body: JSON.stringify({
                itemId: item.id,
                buyerId: Number(currentUser.id),
                sellerId: item.sellerId
            })
        });

        alert(order.success ? "订单创建成功" : order.message);
        if (order.success) {
            loadDetail();
        }
    });
}

async function loadMessages() {
    const result = await request(`/messages/item/${itemId}`);
    const messages = result.data || [];
    const currentUser = getCurrentUser();
    const currentUserId = currentUser && currentUser.id ? Number(currentUser.id) : null;

    messageList.innerHTML = messages.length
        ? messages.map(message => {
            const canManage = currentUserId === Number(message.senderId);
            const senderName = message.senderNickname || `用户 ${message.senderId}`;
            const createdAt = formatMessageTime(message.createdAt);
            return `
            <div class="message-item" data-message-id="${message.id}">
                <div class="message-header">
                    <div class="message-meta">
                        <strong>${escapeHtml(senderName)}</strong>
                        ${createdAt ? `<time datetime="${escapeHtml(message.createdAt)}">${escapeHtml(createdAt)}</time>` : ""}
                    </div>
                    ${canManage ? `
                        <div class="message-actions">
                            <button class="secondary message-action" type="button" data-action="edit">编辑</button>
                            <button class="secondary message-action danger" type="button" data-action="delete">删除</button>
                        </div>
                    ` : ""}
                </div>
                <p class="message-content">${escapeHtml(message.content)}</p>
            </div>
        `;
        }).join("")
        : "<p>暂无留言</p>";
}

messageForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const currentUser = getCurrentUser();
    if (!currentUser || !currentUser.id) {
        alert("请先登录后留言");
        location.href = "profile.html";
        return;
    }
    if (!currentItem) {
        alert("商品信息加载失败");
        return;
    }

    const data = {
        itemId: Number(itemId),
        senderId: Number(currentUser.id),
        receiverId: Number(currentItem.sellerId),
        content: messageForm.content.value
    };

    const result = await request("/messages", {
        method: "POST",
        body: JSON.stringify(data)
    });

    if (!result.success) {
        alert(result.message);
        return;
    }

    messageForm.reset();
    loadMessages();
});

messageList.addEventListener("click", async (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) {
        return;
    }

    const messageItem = button.closest(".message-item");
    const messageId = messageItem && messageItem.dataset.messageId;
    const action = button.dataset.action;

    if (action === "edit") {
        const messageContent = messageItem.querySelector(".message-content");
        const messageActions = messageItem.querySelector(".message-actions");
        const existingForm = messageItem.querySelector(".message-edit-form");
        if (existingForm) {
            existingForm.querySelector("textarea").focus();
            return;
        }

        const editForm = createMessageEditForm(messageContent.textContent);
        messageContent.hidden = true;
        messageActions.hidden = true;
        messageItem.appendChild(editForm);
        editForm.querySelector("textarea").focus();
        return;
    }

    if (action === "cancel") {
        messageItem.querySelector(".message-content").hidden = false;
        messageItem.querySelector(".message-actions").hidden = false;
        messageItem.querySelector(".message-edit-form").remove();
        return;
    }

    if (action === "delete") {
        const currentUser = getCurrentUser();
        if (!currentUser || !currentUser.id) {
            alert("请先登录");
            location.href = "profile.html";
            return;
        }
        if (!confirm("确定删除这条留言吗？")) {
            return;
        }

        const result = await request(`/messages/${messageId}`, {
            method: "DELETE",
            body: JSON.stringify({ senderId: Number(currentUser.id) })
        });

        if (!result.success) {
            alert(result.message);
            return;
        }
        loadMessages();
    }
});

messageList.addEventListener("submit", async (event) => {
    const form = event.target.closest(".message-edit-form");
    if (!form) {
        return;
    }
    event.preventDefault();

    const currentUser = getCurrentUser();
    if (!currentUser || !currentUser.id) {
        alert("请先登录");
        location.href = "profile.html";
        return;
    }

    const messageItem = form.closest(".message-item");
    const messageId = messageItem.dataset.messageId;
    const result = await request(`/messages/${messageId}`, {
        method: "PUT",
        body: JSON.stringify({
            senderId: Number(currentUser.id),
            content: form.content.value
        })
    });

    if (!result.success) {
        alert(result.message);
        return;
    }

    loadMessages();
});

loadDetail();
loadMessages();
