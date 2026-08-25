const listElement = document.querySelector("#conversationList");
const roomElement = document.querySelector("#chatRoom");
const emptyElement = document.querySelector("#chatEmpty");
const messagesElement = document.querySelector("#chatMessages");
const formElement = document.querySelector("#chatForm");
let currentUser = null;
let conversations = [];
let activeConversation = null;
let oldestSequence = null;
let hasOlder = false;
let polling = null;

function h(value) { return String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;"); }
function time(value) { if (!value) return ""; const date = new Date(value); return Number.isNaN(date.getTime()) ? "" : date.toLocaleString("zh-CN", {month:"numeric",day:"numeric",hour:"2-digit",minute:"2-digit"}); }

async function loadConversations(selectId = null) {
    const result = await request("/chat/conversations?size=50");
    if (!result.success) { document.querySelector("#conversationStatus").textContent = result.message || "会话加载失败"; return; }
    conversations = result.data.conversations || [];
    document.querySelector("#chatTotalUnread").textContent = `${result.data.totalUnread || 0} 未读`;
    listElement.innerHTML = conversations.length ? conversations.map(c => `
        <button type="button" class="conversation-card ${activeConversation?.id === c.id ? "active" : ""}" data-conversation-id="${h(c.id)}" role="listitem">
            <img src="${h(productImageUrl(c.itemImageUrl))}" alt=""><span class="conversation-copy"><span><strong>${h(c.otherNickname)}</strong><time>${h(time(c.lastMessageAt))}</time></span><b>${h(c.itemTitle)}</b><small>${h(c.lastMessagePreview || "还没有消息，打个招呼吧")}</small></span>
            ${c.unreadCount ? `<em>${c.unreadCount > 99 ? "99+" : c.unreadCount}</em>` : ""}
        </button>`).join("") : '<p class="empty-state">暂无私聊。去逛逛商品，和卖家聊聊吧。</p>';
    installImageFallbacks(listElement);
    const target = selectId || new URLSearchParams(location.search).get("conversation");
    if (target && (!activeConversation || activeConversation.id !== target)) await selectConversation(target);
    refreshChatUnread();
}

async function selectConversation(id, { preserveReadingPosition = false } = {}) {
    const previousTop = messagesElement.scrollTop;
    const wasNearBottom = messagesElement.scrollHeight - messagesElement.scrollTop - messagesElement.clientHeight < 80;
    const result = await request(`/chat/conversations/${encodeURIComponent(id)}/messages?size=50`);
    if (!result.success) { document.querySelector("#conversationStatus").textContent = result.message || "无法打开该会话"; return; }
    activeConversation = result.data.conversation;
    history.replaceState(null, "", `messages.html?conversation=${encodeURIComponent(id)}`);
    roomElement.hidden = false; emptyElement.hidden = true; document.body.classList.add("chat-room-open");
    paintRoom(); paintMessages(result.data.messages || [], false);
    if (preserveReadingPosition && !wasNearBottom) messagesElement.scrollTop = previousTop;
    hasOlder = result.data.hasMore; oldestSequence = result.data.nextBeforeSequence;
    document.querySelector("#loadOlder").hidden = !hasOlder;
    await markRead();
    await loadConversations();
}

function paintRoom() {
    document.querySelector("#chatItemTitle").textContent = activeConversation.itemTitle;
    document.querySelector("#chatOtherName").textContent = `与 ${activeConversation.otherNickname} 沟通`;
    document.querySelector("#chatItemLink").href = `detail.html?id=${activeConversation.itemId}`;
    document.querySelector("#chatItemImage").src = productImageUrl(activeConversation.itemImageUrl);
    const blocked = activeConversation.blocked;
    document.querySelector("#toggleChatBlock").textContent = activeConversation.blockedByMe ? "解除屏蔽" : "屏蔽";
    formElement.querySelector("textarea").disabled = blocked;
    formElement.querySelector('button[type="submit"]').disabled = blocked;
    document.querySelector("#chatFormMessage").textContent = blocked ? "当前会话已被一方屏蔽，暂时不能发送消息。" : "";
}

function paintMessages(items, prepend) {
    const html = items.map(m => `<article class="chat-bubble ${Number(m.senderId) === Number(currentUser.id) ? "mine" : "theirs"}"><p>${h(m.body)}</p><time>${h(time(m.createdAt))}</time></article>`).join("");
    if (prepend) messagesElement.insertAdjacentHTML("afterbegin", html); else messagesElement.innerHTML = html || '<p class="chat-day-note">还没有消息，先礼貌地打个招呼吧。</p>';
    if (!prepend) messagesElement.scrollTop = messagesElement.scrollHeight;
}

async function markRead() {
    if (!activeConversation?.lastSequence) return;
    await request(`/chat/conversations/${encodeURIComponent(activeConversation.id)}/read`, {method:"POST", body:JSON.stringify({throughSequence: activeConversation.lastSequence})});
}

listElement.addEventListener("click", e => { const button = e.target.closest("[data-conversation-id]"); if (button) selectConversation(button.dataset.conversationId); });
document.querySelector("#chatBack").addEventListener("click", () => { document.body.classList.remove("chat-room-open"); roomElement.hidden = true; emptyElement.hidden = false; });
document.querySelector("#loadOlder").addEventListener("click", async () => {
    const result = await request(`/chat/conversations/${encodeURIComponent(activeConversation.id)}/messages?beforeSequence=${oldestSequence}&size=50`);
    if (!result.success) return; paintMessages(result.data.messages || [], true); hasOlder = result.data.hasMore; oldestSequence = result.data.nextBeforeSequence; document.querySelector("#loadOlder").hidden = !hasOlder;
});
formElement.addEventListener("submit", async e => {
    e.preventDefault(); const button = formElement.querySelector('button[type="submit"]'); button.disabled = true;
    const result = await request(`/chat/conversations/${encodeURIComponent(activeConversation.id)}/messages`, {method:"POST", body:JSON.stringify({body:formElement.body.value})});
    button.disabled = false; if (!result.success) { document.querySelector("#chatFormMessage").textContent = result.message || "发送失败"; return; }
    formElement.reset(); await selectConversation(activeConversation.id);
});
formElement.body.addEventListener("keydown", e => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); formElement.requestSubmit(); } });
document.querySelector("#toggleChatBlock").addEventListener("click", async () => {
    const unblock = activeConversation.blockedByMe; const result = await request(`/chat/blocks/${activeConversation.otherUserId}`, {method:unblock ? "DELETE" : "PUT"});
    if (!result.success) return; await selectConversation(activeConversation.id);
});
document.querySelector("#reportChatUser").addEventListener("click", async () => { if (await openReportDialog("USER", activeConversation.otherUserId, activeConversation.otherNickname)) alert("举报已提交"); });

async function poll() { if (document.hidden) return; if (activeConversation) await selectConversation(activeConversation.id, { preserveReadingPosition: true }); else await loadConversations(); }
(async () => {
    currentUser = await requireAuthenticatedUser({message:"登录后才能查看私聊消息，是否前往登录？", returnTo:"messages.html"});
    if (!currentUser) return; await loadConversations(); polling = setInterval(poll, 8000);
})();
window.addEventListener("beforeunload", () => clearInterval(polling));
