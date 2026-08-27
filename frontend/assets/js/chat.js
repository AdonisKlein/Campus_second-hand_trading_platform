const $ = selector => document.querySelector(selector);
const listElement = $("#conversationList"), roomElement = $("#chatRoom"), emptyElement = $("#chatEmpty"), contextElement = $("#chatContext"), messagesElement = $("#chatMessages"), formElement = $("#chatForm");
let currentUser, conversations = [], activeConversation, oldestSequence, hasOlder = false, polling, page = 0, hasNextPage = false, pendingBody = "";
const h = value => String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
const dateTime = value => { const d = new Date(value); return Number.isNaN(d.getTime()) ? "" : d.toLocaleString("zh-CN", {month:"numeric",day:"numeric",hour:"2-digit",minute:"2-digit"}); };
const day = value => { const d = new Date(value); return Number.isNaN(d.getTime()) ? "" : d.toLocaleDateString("zh-CN", {year:"numeric",month:"long",day:"numeric"}); };
const stateLabel = value => ({ON_SALE:"在售",RESERVED:"已预留",SOLD:"已售出",WITHDRAWN:"已下架"}[value] || "状态待确认");
const money = value => value == null ? "" : `¥${Number(value).toFixed(2)}`;

function visibleConversations() {
    const query = $("#conversationSearch").value.trim().toLowerCase();
    const result = conversations.filter(c => !query || `${c.otherNickname} ${c.itemTitle} ${c.lastMessagePreview || ""}`.toLowerCase().includes(query));
    return $("#unreadFirst").checked ? result.sort((a,b) => Number(Boolean(b.unreadCount))-Number(Boolean(a.unreadCount)) || new Date(b.lastMessageAt||0)-new Date(a.lastMessageAt||0)) : result;
}
function paintConversations() {
    const rows = visibleConversations();
    listElement.innerHTML = rows.length ? rows.map(c => `<button type="button" class="conversation-card ${activeConversation?.id===c.id?"active":""}" data-conversation-id="${h(c.id)}" role="listitem"><img src="${h(productImageUrl(c.itemImageUrl))}" alt=""><span class="conversation-copy"><span><strong>${h(c.otherNickname)}</strong><time>${h(dateTime(c.lastMessageAt))}</time></span><b>${h(c.itemTitle)}</b><small>${h(c.lastMessagePreview||"还没有消息，打个招呼吧")}</small></span>${c.unreadCount?`<em>${c.unreadCount>99?"99+":c.unreadCount}</em>`:""}</button>`).join("") : '<p class="empty-state">没有匹配的会话。</p>';
    installImageFallbacks(listElement);
}
async function loadConversations(selectId=null, append=false) {
    $("#conversationStatus").textContent = append ? "正在加载…" : "";
    const result = await request(`/chat/conversations?page=${append?page:0}&size=30`);
    if (!result.success) { $("#conversationStatus").textContent = result.message || "会话加载失败"; return; }
    page = result.data.page || 0; hasNextPage = Boolean(result.data.hasNext);
    conversations = append ? conversations.concat(result.data.conversations||[]) : (result.data.conversations||[]);
    $("#chatTotalUnread").textContent = `${result.data.totalUnread||0} 条未读`; $("#loadMoreConversations").hidden = !hasNextPage; $("#conversationStatus").textContent = ""; paintConversations();
    const target = selectId || new URLSearchParams(location.search).get("conversation");
    if (target && activeConversation?.id !== target) await selectConversation(target);
    refreshChatUnread();
}
async function selectConversation(id,{preserveReadingPosition=false}={}) {
    const previousTop=messagesElement.scrollTop, nearBottom=messagesElement.scrollHeight-messagesElement.scrollTop-messagesElement.clientHeight<80;
    $("#chatConnection").textContent="正在同步…";
    const result=await request(`/chat/conversations/${encodeURIComponent(id)}/messages?size=50`);
    if(!result.success){$("#chatConnection").textContent="连接中断";$("#conversationStatus").textContent=result.message||"无法打开该会话";return;}
    activeConversation=result.data.conversation; history.replaceState(null,"",`messages.html?conversation=${encodeURIComponent(id)}`); roomElement.hidden=false;contextElement.hidden=false;emptyElement.hidden=true;document.body.classList.add("chat-room-open");
    paintRoom();paintMessages(result.data.messages||[],false);if(preserveReadingPosition&&!nearBottom)messagesElement.scrollTop=previousTop;
    hasOlder=result.data.hasMore;oldestSequence=result.data.nextBeforeSequence;$("#loadOlder").hidden=!hasOlder;$("#chatConnection").textContent="已连接";await markRead();await loadConversations();
}
function paintRoom(){
    const c=activeConversation, image=productImageUrl(c.itemImageUrl), href=`detail.html?id=${c.itemId}`;
    $("#chatOtherName").textContent=c.otherNickname; $("#chatItemTitle").textContent=$("#contextItemTitle").textContent=c.itemTitle; $("#chatItemImage").src=$("#contextItemImage").src=image; $("#chatItemLink").href=$("#contextItemLink").href=href;
    $("#chatItemPrice").textContent=$("#contextItemPrice").textContent=money(c.itemPrice); $("#chatItemState").textContent=$("#contextItemState").textContent=stateLabel(c.itemStatus);
    $("#toggleChatBlock").textContent=c.blockedByMe?"解除屏蔽":"屏蔽"; formElement.body.disabled=c.blocked; formElement.querySelector('button[type="submit"]').disabled=c.blocked; $("#chatFormMessage").textContent=c.blocked?"当前会话已被一方屏蔽，暂时不能发送消息。":""; paintConversations(); installImageFallbacks(roomElement); installImageFallbacks(contextElement);
}
function paintMessages(items,prepend){let lastDay="";const html=items.map(m=>{const currentDay=day(m.createdAt),divider=currentDay!==lastDay?`<p class="chat-day-note">${h(currentDay)}</p>`:"";lastDay=currentDay;return `${divider}<article class="chat-bubble ${Number(m.senderId)===Number(currentUser.id)?"mine":"theirs"}"><p>${h(m.body)}</p><time>${h(dateTime(m.createdAt))}</time></article>`;}).join("");if(prepend)messagesElement.insertAdjacentHTML("afterbegin",html);else messagesElement.innerHTML=html||'<p class="chat-day-note">还没有消息，先礼貌地打个招呼吧。</p>';if(!prepend)messagesElement.scrollTop=messagesElement.scrollHeight;}
async function markRead(){if(activeConversation?.lastSequence)await request(`/chat/conversations/${encodeURIComponent(activeConversation.id)}/read`,{method:"POST",body:JSON.stringify({throughSequence:activeConversation.lastSequence})});}
async function sendMessage(body){const button=formElement.querySelector('button[type="submit"]');button.disabled=true;$("#chatFormMessage").textContent="正在发送…";const result=await request(`/chat/conversations/${encodeURIComponent(activeConversation.id)}/messages`,{method:"POST",body:JSON.stringify({body})});button.disabled=false;if(!result.success){pendingBody=body;$("#chatFormMessage").innerHTML=`${h(result.message||"发送失败")} <button type="button" class="text-action" id="retryMessage">重试</button>`;return;}pendingBody="";formElement.reset();$("#chatFormMessage").textContent="";await selectConversation(activeConversation.id);}
listElement.addEventListener("click",e=>{const b=e.target.closest("[data-conversation-id]");if(b)selectConversation(b.dataset.conversationId);});
$("#conversationSearch").addEventListener("input",paintConversations);$("#unreadFirst").addEventListener("change",paintConversations);$("#loadMoreConversations").addEventListener("click",()=>{page+=1;loadConversations(null,true);});
$("#chatBack").addEventListener("click",()=>{document.body.classList.remove("chat-room-open");roomElement.hidden=true;contextElement.hidden=true;emptyElement.hidden=false;history.replaceState(null,"","messages.html");});
$("#loadOlder").addEventListener("click",async()=>{$("#historyStatus").textContent="正在加载…";const r=await request(`/chat/conversations/${encodeURIComponent(activeConversation.id)}/messages?beforeSequence=${oldestSequence}&size=50`);$("#historyStatus").textContent="";if(!r.success)return;paintMessages(r.data.messages||[],true);hasOlder=r.data.hasMore;oldestSequence=r.data.nextBeforeSequence;$("#loadOlder").hidden=!hasOlder;});
formElement.addEventListener("submit",e=>{e.preventDefault();const body=formElement.body.value.trim();if(body)sendMessage(body);});formElement.body.addEventListener("keydown",e=>{if(e.key==="Enter"&&!e.shiftKey){e.preventDefault();formElement.requestSubmit();}});$("#chatFormMessage").addEventListener("click",e=>{if(e.target.id==="retryMessage"&&pendingBody)sendMessage(pendingBody);});
$("#toggleChatBlock").addEventListener("click",async()=>{const unblock=activeConversation.blockedByMe,r=await request(`/chat/blocks/${activeConversation.otherUserId}`,{method:unblock?"DELETE":"PUT"});if(r.success)await selectConversation(activeConversation.id);});
$("#reportChatUser").addEventListener("click",async()=>{if(await openReportDialog("USER",activeConversation.otherUserId,activeConversation.otherNickname,{conversationId:activeConversation.id}))$("#chatFormMessage").textContent="举报已提交，管理员会按规则处理。";});
async function poll(){if(document.hidden)return;try{if(activeConversation)await selectConversation(activeConversation.id,{preserveReadingPosition:true});else await loadConversations();}catch(_){$("#chatConnection").textContent="连接中断，正在重试…";}}
(async()=>{currentUser=await requireAuthenticatedUser({message:"登录后才能查看私聊消息，是否前往登录？",returnTo:"messages.html"});if(!currentUser)return;await loadConversations();polling=setInterval(poll,8000);})();window.addEventListener("beforeunload",()=>clearInterval(polling));
