const inventoryList = document.querySelector("#inventoryList");
const inventoryCount = document.querySelector("#inventoryCount");
const itemEditor = document.querySelector("#itemEditor");
const itemEditorForm = document.querySelector("#itemEditorForm");
const itemEditorMessage = document.querySelector("#itemEditorMessage");
const inventoryMessage = document.querySelector("#inventoryMessage");
let ownedItems = [];

function escapeHtml(value) {
    return String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
}

function itemStatus(item) {
    if (item.moderationStatus === "REMOVED") return { label: "管理员已下架", className: "cancelled" };
    return {
        ON_SALE: { label: "正在出售", className: "completed" },
        RESERVED: { label: "交易处理中", className: "waiting" },
        SOLD: { label: "已售出", className: "completed" },
        WITHDRAWN: { label: "卖家已下架", className: "cancelled" }
    }[item.status] || { label: item.status, className: "" };
}

function renderItem(item) {
    const status = itemStatus(item);
    const actions = (item.allowedActions || []).map(action => {
        const label = action === "WITHDRAW" ? "下架商品" : "重新上架";
        return `<button type="button" class="secondary" data-inventory-action="${action}" data-item-id="${item.id}">${label}</button>`;
    }).join("");
    return `
        <article class="inventory-card" data-item-id="${item.id}">
            <img src="${escapeHtml(item.imageUrl || "assets/images/placeholder.svg")}" alt="${escapeHtml(item.title)}">
            <div class="inventory-card__content">
                <div class="inventory-card__meta"><span class="tag">${escapeHtml(item.category)}</span><span class="status-badge ${status.className}">${escapeHtml(status.label)}</span></div>
                <h3>${escapeHtml(item.title)}</h3>
                <p class="item-description">${escapeHtml(item.description || "暂未填写商品描述")}</p>
                <div class="inventory-card__footer"><strong class="price">￥${Number(item.price).toFixed(2)}</strong><small>商品 #${item.id}</small></div>
            </div>
            <div class="inventory-card__actions">
                ${item.editable ? `<button type="button" data-edit-item="${item.id}">编辑资料</button>` : ""}
                ${actions}
                ${!item.editable && !actions ? '<span class="inventory-locked">当前状态不可修改</span>' : ""}
            </div>
        </article>`;
}

function updateCounts(items) {
    const visibleItems = items.filter(item => item.moderationStatus !== "REMOVED");
    const count = status => visibleItems.filter(item => item.status === status).length;
    document.querySelector("#onSaleCount").textContent = count("ON_SALE");
    document.querySelector("#reservedCount").textContent = count("RESERVED");
    document.querySelector("#withdrawnCount").textContent = count("WITHDRAWN");
    document.querySelector("#soldCount").textContent = count("SOLD");
    document.querySelector("#moderatedCount").textContent = items.filter(item => item.moderationStatus === "REMOVED").length;
}

function showInventoryMessage(message, isError = false) {
    inventoryMessage.textContent = message;
    inventoryMessage.classList.toggle("error", isError);
}

async function loadInventory() {
    const currentUser = await session.current();
    if (!currentUser) {
        location.href = "profile.html";
        return;
    }
    const result = await request("/items/mine");
    if (!result.success) {
        inventoryCount.textContent = "加载失败";
        showInventoryMessage(result.message || "商品加载失败", true);
        inventoryList.innerHTML = `<p class="empty-state">${escapeHtml(result.message || "商品加载失败")}</p>`;
        return;
    }
    showInventoryMessage("");
    ownedItems = result.data || [];
    inventoryCount.textContent = `${ownedItems.length} 件商品`;
    updateCounts(ownedItems);
    inventoryList.innerHTML = ownedItems.length ? ownedItems.map(renderItem).join("")
        : '<div class="empty-state"><strong>还没有发布商品</strong><p>整理一件闲置，让它在校园里重新发挥价值。</p><a class="button-link compact-link" href="publish.html">发布第一件商品</a></div>';
}

function openEditor(itemId) {
    const item = ownedItems.find(candidate => Number(candidate.id) === Number(itemId));
    if (!item || !item.editable) return;
    itemEditorForm.itemId.value = item.id;
    itemEditorForm.title.value = item.title || "";
    itemEditorForm.category.value = item.category || "其他";
    itemEditorForm.price.value = item.price;
    itemEditorForm.imageUrl.value = item.imageUrl || "";
    itemEditorForm.description.value = item.description || "";
    itemEditorMessage.textContent = "";
    itemEditor.showModal();
}

inventoryList.addEventListener("click", async event => {
    const editButton = event.target.closest("button[data-edit-item]");
    if (editButton) {
        openEditor(editButton.dataset.editItem);
        return;
    }
    const actionButton = event.target.closest("button[data-inventory-action]");
    if (!actionButton) return;
    const action = actionButton.dataset.inventoryAction;
    const prompt = action === "WITHDRAW" ? "确定暂时下架这件商品吗？" : "确定重新上架这件商品吗？";
    if (!confirm(prompt)) return;
    actionButton.disabled = true;
    const result = await request(`/items/${actionButton.dataset.itemId}/seller-actions`, {
        method: "POST", body: JSON.stringify({ action })
    });
    if (!result.success) {
        showInventoryMessage(result.message || "操作失败", true);
        actionButton.disabled = false;
        return;
    }
    await loadInventory();
    showInventoryMessage(action === "WITHDRAW" ? "商品已下架" : "商品已重新上架");
});

document.querySelectorAll("[data-close-editor]").forEach(button => {
    button.addEventListener("click", () => itemEditor.close());
});

itemEditorForm.addEventListener("submit", async event => {
    event.preventDefault();
    const submitButton = itemEditorForm.querySelector('button[type="submit"]');
    const editorButtons = [...itemEditorForm.querySelectorAll("button")];
    editorButtons.forEach(button => button.disabled = true);
    const data = formToJson(itemEditorForm);
    const itemId = data.itemId;
    delete data.itemId;
    data.price = Number(data.price);
    const result = await request(`/items/${itemId}`, { method: "PUT", body: JSON.stringify(data) });
    editorButtons.forEach(button => button.disabled = false);
    if (!result.success) {
        itemEditorMessage.textContent = result.message || "保存失败";
        return;
    }
    itemEditor.close();
    await loadInventory();
    showInventoryMessage("商品资料已保存");
});

loadInventory();
