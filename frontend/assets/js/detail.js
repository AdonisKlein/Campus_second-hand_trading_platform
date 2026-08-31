const params = new URLSearchParams(location.search);
const itemId = params.get("id");
const validItemId = /^[1-9]\d*$/.test(itemId || "");
const itemDetail = document.querySelector("#itemDetail");
const messageList = document.querySelector("#messageList");
const messageForm = document.querySelector("#messageForm");
const mobileProductActions = document.querySelector("#mobileProductActions");
let currentItem = null;

function escapeHtml(value) {
    return String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
}

function formatPrice(value) {
    return Number(value) === 0 ? "免费" : `￥${Number(value).toFixed(2)}`;
}

function formatDate(value) {
    if (!value) return "";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value).replace("T", " ").slice(0, 16);
    return new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit" }).format(date);
}

function formatActivity(value) {
    if (!value) return "近期活跃";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "近期活跃";
    const days = Math.max(0, Math.floor((Date.now() - date.getTime()) / 86400000));
    if (days === 0) return "今天活跃";
    if (days === 1) return "昨天活跃";
    return `${days} 天前活跃`;
}

function itemAvailability(item) {
    if (item.moderationStatus === "REMOVED") {
        return { tone: "unavailable", label: "管理员已下架", note: "该商品因平台治理暂不对其他用户展示，你可以前往商品管理查看处理状态。" };
    }
    const states = {
        ON_SALE: { tone: "selling", label: "正在出售", note: "提交购买意向不会锁定商品，卖家选定买家后才会预留。" },
        RESERVED: { tone: "reserved", label: "已为买家预留", note: "卖家已经选定买家，正在等待双方当面交接。" },
        SOLD: { tone: "sold", label: "交易已完成", note: "该商品已经完成当面交易。" },
        WITHDRAWN: { tone: "unavailable", label: "卖家已下架", note: "该商品已停止公开展示，你可以前往商品管理重新上架。" }
    };
    return states[item.status] || { tone: "unavailable", label: "当前不可交易", note: "商品状态正在处理中。" };
}

function hasAction(item, action) {
    return (item.viewer?.availableActions || []).includes(action);
}

function actionButtons(item, mobile = false) {
    const buttons = [];
    if (hasAction(item, "CHAT_SELLER")) buttons.push('<button type="button" class="secondary" data-product-action="chat">私聊卖家</button>');
    if (hasAction(item, "REQUEST_PURCHASE")) buttons.push('<button type="button" class="order-cta" data-product-action="request">我想要</button>');
    if (hasAction(item, "VIEW_PURCHASE_REQUEST")) buttons.push('<a class="button-link order-cta" href="orders.html" data-requires-auth>查看交易进度</a>');
    if (hasAction(item, "MANAGE_LISTING")) buttons.push('<a class="button-link order-cta" href="my-items.html" data-requires-auth>管理这件商品</a>');
    if (!buttons.length) buttons.push(`<button type="button" disabled>${escapeHtml(itemAvailability(item).label)}</button>`);
    return `<div class="${mobile ? "mobile-action-buttons" : "detail-primary-actions"}">${buttons.join("")}</div>`;
}

function purchaseRequestNotice(item) {
    const request = item.viewer?.purchaseRequest;
    if (!request) return "";
    const waiting = request.status === "WAITING_HANDOVER";
    return `<div class="purchase-request-notice ${waiting ? "selected" : ""}">
        <span aria-hidden="true">${waiting ? "✓" : "⌛"}</span><div><strong>${waiting ? "卖家已选择你" : "购买意向已提交"}</strong>
        <p>${waiting ? "请在订单页查看有效期，并通过私聊约定安全的校内交接地点。" : "商品仍会继续展示，请等待卖家回应；你可以在订单页取消意向。"}</p></div></div>`;
}

function relatedItems(items) {
    if (!items?.length) return '<p class="empty-state">这位卖家暂时没有其他在售商品。</p>';
    return `<div class="seller-listing-grid">${items.map(item => `
        <a class="seller-listing-card" href="detail.html?id=${encodeURIComponent(item.id)}">
            <img src="${escapeHtml(productImageUrl(item.imageUrl))}" alt="${escapeHtml(item.title)}">
            <div><strong>${escapeHtml(item.title)}</strong><span>${escapeHtml(formatPrice(item.price))}</span></div>
        </a>`).join("")}</div>`;
}

function renderDetail(item) {
    const availability = itemAvailability(item);
    const tags = (item.tags || []).map(tag => `<span class="product-tag">${escapeHtml(tag)}</span>`).join("");
    const sellerInitial = (item.seller?.displayName || "航").slice(0, 1);
    const report = hasAction(item, "REPORT_ITEM") ? '<button type="button" class="text-action danger" data-product-action="report">举报商品</button>' : "";
    itemDetail.innerHTML = `
        <article class="product-hero">
            <div class="product-gallery">
                <div class="product-main-image"><img src="${escapeHtml(productImageUrl(item.imageUrl))}" alt="${escapeHtml(item.title)}"></div>
                <button class="product-thumbnail active" type="button" aria-label="查看商品主图"><img src="${escapeHtml(productImageUrl(item.imageUrl))}" alt=""></button>
                <p>平台受控图片 · 已清除照片定位等元数据</p>
            </div>
            <div class="product-commerce">
                <div class="product-status-row"><span class="product-state ${availability.tone}">${escapeHtml(availability.label)}</span><span>${escapeHtml(item.region || "校内交易")}</span></div>
                <h1>${escapeHtml(item.title)}</h1>
                <div class="product-meta"><span>发布于 ${escapeHtml(formatDate(item.createdAt))}</span><span>建议当面验货</span></div>
                <p class="product-price"><small>校园转让价</small>${escapeHtml(formatPrice(item.price))}</p>
                <div class="product-tags">${tags || '<span class="product-tag neutral">暂无交易标签</span>'}</div>
                ${purchaseRequestNotice(item)}
                <div class="seller-profile-card">
                    <span class="seller-profile-avatar" aria-hidden="true">${escapeHtml(sellerInitial)}</span>
                    <div><p>发布同学</p><h2>${escapeHtml(item.seller?.displayName || "校园用户")}</h2><span>${escapeHtml(item.seller?.region || item.region || "校内")}</span></div>
                    <dl><div><dt>信用</dt><dd>${escapeHtml(item.seller?.creditScore ?? "—")}</dd></div><div><dt>在售</dt><dd>${escapeHtml(item.seller?.onSaleCount ?? 0)}</dd></div></dl>
                    <span class="seller-active">${escapeHtml(formatActivity(item.seller?.lastActiveAt))}</span>
                </div>
                <p class="availability-note">${escapeHtml(availability.note)}</p>
                ${actionButtons(item)}
                <div class="detail-quiet-actions">${report}</div>
            </div>
        </article>
        <section class="product-information product-information--single">
            <article class="product-description-card"><h2>商品详情与成色</h2>
                <p>${escapeHtml(item.description || "卖家暂未填写详细描述，建议私聊确认成色、配件和瑕疵后再提交购买意向。")}</p>
            </article>
        </section>
        <section class="seller-more-section"><div class="section-heading"><h2>这位卖家的其他在售</h2><span>${escapeHtml(item.seller?.displayName || "校园卖家")}</span></div>${relatedItems(item.sellerItems)}</section>`;
    installImageFallbacks(itemDetail);
    mobileProductActions.innerHTML = `<span><small>${escapeHtml(availability.label)}</small><strong>${escapeHtml(formatPrice(item.price))}</strong></span>${actionButtons(item, true)}`;
    mobileProductActions.hidden = false;
}

async function loadDetail() {
    const result = await request(`/items/${itemId}`);
    if (!result?.success || !result.data) {
        itemDetail.innerHTML = '<p class="empty-state" role="alert">商品不存在、已下架或暂时无法查看。</p>';
        mobileProductActions.hidden = true;
        return;
    }
    currentItem = result.data;
    renderDetail(currentItem);
}

async function handleProductAction(action, button) {
    if (!currentItem) return;
    if (action === "report") {
        if (await openReportDialog("ITEM", currentItem.id, currentItem.title)) alert("举报已提交，可在个人中心查看处理进度");
        return;
    }
    button.disabled = true;
    const prompt = action === "chat" ? "登录后才能私聊卖家，是否前往登录？" : "登录后才能提交购买意向，是否前往登录？";
    const user = await requireAuthenticatedUser({ message: prompt, returnTo: location.pathname + location.search });
    if (!user) { button.disabled = false; return; }
    if (action === "chat") {
        const result = await request("/chat/conversations", { method: "POST", body: JSON.stringify({ itemId: currentItem.id }) });
        if (result.success) location.href = `messages.html?conversation=${encodeURIComponent(result.data.id)}`;
        else { alert(result.message || "暂时无法发起私聊"); button.disabled = false; }
        return;
    }
    const result = await request("/orders", { method: "POST", body: JSON.stringify({ itemId: currentItem.id }) });
    if (result.success) await loadDetail();
    else { alert(result.message || "暂时无法提交购买意向"); button.disabled = false; }
}

document.addEventListener("click", event => {
    const button = event.target.closest("button[data-product-action]");
    if (button) handleProductAction(button.dataset.productAction, button);
});

function createMessageEditForm(content) {
    const form = document.createElement("form");
    form.className = "message-edit-form";
    form.innerHTML = `<textarea name="content" rows="3" maxlength="500" aria-label="编辑留言内容" required>${escapeHtml(content)}</textarea>
        <div class="message-edit-actions"><button type="submit">保存</button><button class="secondary" type="button" data-action="cancel">取消</button></div>`;
    return form;
}

async function loadMessages() {
    const result = await request(`/messages/item/${itemId}`);
    if (!result?.success) {
        messageList.innerHTML = '<p class="empty-state" role="alert">公开问答暂时加载失败，请稍后重试。</p>';
        return;
    }
    const messages = result.data || [];
    const currentUser = await session.current();
    const currentUserId = currentUser?.id ? Number(currentUser.id) : null;
    messageList.innerHTML = messages.length ? messages.map(message => {
        const own = currentUserId === Number(message.senderId);
        return `<article class="message-item" data-message-id="${message.id}">
            <div class="message-header"><div class="message-meta"><span class="question-avatar" aria-hidden="true">问</span><div><strong>${escapeHtml(message.senderNickname || `用户 ${message.senderId}`)}</strong><time datetime="${escapeHtml(message.createdAt)}">${escapeHtml(formatDate(message.createdAt))}</time></div></div>
            <div class="message-actions">${own ? '<button class="secondary message-action" type="button" data-action="edit">编辑</button><button class="secondary message-action danger" type="button" data-action="delete">删除</button>' : '<button class="secondary message-action" type="button" data-action="report">举报</button>'}</div></div>
            <p class="message-content">${escapeHtml(message.content)}</p></article>`;
    }).join("") : '<p class="empty-state">还没有公开问题。你可以先问问配件、尺寸或使用情况。</p>';
}

messageForm.addEventListener("submit", async event => {
    event.preventDefault();
    const user = await requireAuthenticatedUser({ message: "登录后才能发布公开问题，是否前往登录？", returnTo: location.pathname + location.search });
    if (!user || !currentItem) return;
    const submit = messageForm.querySelector("button[type='submit']");
    submit.disabled = true;
    const content = messageForm.elements.namedItem("content").value;
    const result = await request("/messages", { method: "POST", body: JSON.stringify({ itemId: Number(itemId), content }) });
    submit.disabled = false;
    if (!result.success) { alert(result.message); return; }
    messageForm.reset();
    loadMessages();
});

messageList.addEventListener("click", async event => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const messageItem = button.closest(".message-item");
    const messageId = messageItem?.dataset.messageId;
    const action = button.dataset.action;
    if (action === "report") {
        const summary = messageItem.querySelector(".message-content")?.textContent || "这条留言";
        if (await openReportDialog("MESSAGE", messageId, summary.slice(0, 40))) alert("举报已提交，可在个人中心查看处理进度");
        return;
    }
    if (action === "edit") {
        if (messageItem.querySelector(".message-edit-form")) return;
        const content = messageItem.querySelector(".message-content");
        content.hidden = true;
        messageItem.querySelector(".message-actions").hidden = true;
        messageItem.appendChild(createMessageEditForm(content.textContent));
        messageItem.querySelector("textarea").focus();
        return;
    }
    if (action === "cancel") {
        messageItem.querySelector(".message-content").hidden = false;
        messageItem.querySelector(".message-actions").hidden = false;
        messageItem.querySelector(".message-edit-form").remove();
        return;
    }
    if (action === "delete" && confirm("确定删除这条公开问题吗？")) {
        const result = await request(`/messages/${messageId}`, { method: "DELETE" });
        if (result.success) loadMessages(); else alert(result.message);
    }
});

messageList.addEventListener("submit", async event => {
    const form = event.target.closest(".message-edit-form");
    if (!form) return;
    event.preventDefault();
    const messageId = form.closest(".message-item").dataset.messageId;
    const result = await request(`/messages/${messageId}`, { method: "PUT", body: JSON.stringify({ content: form.content.value }) });
    if (result.success) loadMessages(); else alert(result.message);
});

if (validItemId) {
    loadDetail();
    loadMessages();
} else {
    itemDetail.innerHTML = '<p class="empty-state" role="alert">商品编号无效，请返回商品列表重新选择。</p>';
    messageForm.hidden = true;
}
