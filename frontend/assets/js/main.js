const itemGrid = document.querySelector("#itemGrid");
const userGrid = document.querySelector("#userGrid");
const itemCount = document.querySelector("#itemCount");
const searchForm = document.querySelector("#searchForm");
const filterForm = document.querySelector("#filterForm");
const searchFilters = document.querySelector("#searchFilters");
const loadMoreButton = document.querySelector("#loadMore");
const state = { q: "", scope: "ITEMS", sort: "NEWEST", minPrice: "", maxPrice: "", region: "", tags: new Set(), sellerId: null, page: 0 };
let accumulatedItems = [];
let accumulatedUsers = [];

function escapeHtml(value) {
    return String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
}

function searchParams() {
    const params = new URLSearchParams({ scope: state.scope, sort: state.sort, page: state.page, size: 24 });
    if (state.q) params.set("q", state.q);
    if (state.scope === "ITEMS") {
        if (state.minPrice) params.set("minPrice", state.minPrice);
        if (state.maxPrice) params.set("maxPrice", state.maxPrice);
        if (state.sellerId) params.set("sellerId", state.sellerId);
        state.tags.forEach(tag => params.append("tags", tag));
    }
    if (state.region) params.set("region", state.region);
    return params;
}

async function runSearch({ append = false, revealFilters = false } = {}) {
    if (revealFilters) searchFilters.hidden = false;
    itemCount.textContent = "搜索中";
    const result = await request(`/search?${searchParams()}`);
    if (!result || !result.success) {
        itemCount.textContent = "搜索失败";
        loadMoreButton.hidden = true;
        const error = `<p class="empty-state" role="alert">${escapeHtml(result?.message || "暂时无法搜索，请稍后重试")}</p>`;
        const showingUsers = state.scope === "USERS";
        itemGrid.hidden = showingUsers;
        userGrid.hidden = !showingUsers;
        (showingUsers ? userGrid : itemGrid).innerHTML = error;
        return;
    }
    const page = result.data;
    if (state.scope === "USERS") {
        accumulatedUsers = append ? accumulatedUsers.concat(page.users || []) : (page.users || []);
        renderUsers(accumulatedUsers);
    } else {
        accumulatedItems = append ? accumulatedItems.concat(page.items || []) : (page.items || []);
        renderItems(accumulatedItems);
    }
    loadMoreButton.hidden = !page.hasNext;
    document.querySelector("#resultKicker").textContent = state.q || state.sellerId ? "SEARCH RESULTS" : "FRESH ON CAMPUS";
}

function renderItems(items) {
    itemGrid.hidden = false;
    userGrid.hidden = true;
    document.querySelector("#resultTitle").textContent = state.sellerId ? "该用户发布的商品" : (state.q ? `“${state.q}”的商品` : "最新物品");
    itemCount.textContent = `${items.length} 件商品`;
    itemGrid.innerHTML = items.length ? items.map(renderItem).join("")
        : '<p class="empty-state">暂时没有符合条件的商品，换组关键词或清除筛选试试吧。</p>';
    installImageFallbacks(itemGrid);
}

function renderItem(item) {
    const image = productImageUrl(item.imageUrl);
    const price = Number(item.price) === 0 ? "免费" : `￥${Number(item.price).toFixed(2)}`;
    const tags = (item.tags || []).map(tag => `<span>${escapeHtml(tag)}</span>`).join("");
    return `
        <a class="item-card" href="detail.html?id=${encodeURIComponent(item.id)}">
            <div class="item-media"><img src="${escapeHtml(image)}" alt="${escapeHtml(item.title)}">${tags ? `<div class="item-tag-stack">${tags}</div>` : ""}</div>
            <div class="item-card-body">
                <h3>${escapeHtml(item.title)}</h3>
                <p class="item-description">${escapeHtml(item.description || "卖家暂未填写商品描述")}</p>
                <div class="item-seller"><span class="seller-avatar" aria-hidden="true">航</span><span>${escapeHtml(item.sellerNickname || `校园用户 #${item.sellerId}`)} · ${escapeHtml(item.region)}</span></div>
                <div class="item-card-footer"><p class="price">${price}</p><span>信用 ${escapeHtml(item.sellerCreditScore)}</span></div>
            </div>
        </a>`;
}

function renderUsers(users) {
    itemGrid.hidden = true;
    userGrid.hidden = false;
    document.querySelector("#resultTitle").textContent = state.q ? `“${state.q}”的用户` : "校园用户";
    itemCount.textContent = `${users.length} 位用户`;
    userGrid.innerHTML = users.length ? users.map(user => `
        <article class="user-result-card">
            <span class="profile-avatar" aria-hidden="true">${escapeHtml((user.nickname || user.username || "航").slice(0, 1))}</span>
            <div><h3>${escapeHtml(user.nickname || user.username)}</h3><p>@${escapeHtml(user.username)} · ${escapeHtml(user.region || "未设置区域")}</p><div class="user-result-meta"><span>信用 ${escapeHtml(user.creditScore)}</span><span>${formatActivity(user.lastActiveAt)}</span></div></div>
            <button type="button" class="secondary" data-view-seller="${user.id}">查看在售</button>
        </article>`).join("") : '<p class="empty-state">没有找到匹配的校园用户。</p>';
}

function formatActivity(value) {
    if (!value) return "暂无活跃记录";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "最近活跃";
    const days = Math.floor((Date.now() - date.getTime()) / 86400000);
    if (days <= 0) return "今天活跃";
    if (days === 1) return "昨天活跃";
    return `${days} 天前活跃`;
}

function resetPageAndSearch(options = {}) {
    state.page = 0;
    accumulatedItems = [];
    accumulatedUsers = [];
    runSearch(options);
}

function syncScopeUi() {
    document.querySelectorAll("[data-search-scope]").forEach(button => {
        const active = button.dataset.searchScope === state.scope;
        button.classList.toggle("active", active);
        button.setAttribute("aria-selected", String(active));
    });
    document.querySelectorAll("[data-item-filter]").forEach(element => { element.hidden = state.scope !== "ITEMS"; });
    document.querySelectorAll("#searchSort option[value^='PRICE']").forEach(option => { option.disabled = state.scope !== "ITEMS"; });
    if (state.scope === "USERS" && state.sort.startsWith("PRICE")) {
        state.sort = "RELEVANCE";
        document.querySelector("#searchSort").value = state.sort;
    }
}

searchForm.addEventListener("submit", event => {
    event.preventDefault();
    state.q = document.querySelector("#keyword").value.trim();
    state.sellerId = null;
    state.sort = "RELEVANCE";
    document.querySelector("#searchSort").value = state.sort;
    resetPageAndSearch({ revealFilters: true });
});

filterForm.addEventListener("submit", event => {
    event.preventDefault();
    state.sort = document.querySelector("#searchSort").value;
    state.minPrice = document.querySelector("#minPrice").value;
    state.maxPrice = document.querySelector("#maxPrice").value;
    state.region = document.querySelector("#searchRegion").value;
    resetPageAndSearch({ revealFilters: true });
});

document.querySelectorAll("[data-search-scope]").forEach(button => button.addEventListener("click", () => {
    state.scope = button.dataset.searchScope;
    state.sellerId = null;
    syncScopeUi();
    resetPageAndSearch({ revealFilters: true });
}));

document.querySelectorAll("[data-tag-filter]").forEach(button => button.addEventListener("click", () => {
    const tag = button.dataset.tagFilter;
    state.tags.has(tag) ? state.tags.delete(tag) : state.tags.add(tag);
    button.classList.toggle("active", state.tags.has(tag));
    button.setAttribute("aria-pressed", String(state.tags.has(tag)));
    resetPageAndSearch({ revealFilters: true });
}));

document.querySelectorAll("[data-discovery-tag]").forEach(button => button.addEventListener("click", () => {
    state.scope = "ITEMS";
    state.tags = new Set([button.dataset.discoveryTag]);
    searchFilters.hidden = false;
    syncScopeUi();
    document.querySelectorAll("[data-tag-filter]").forEach(filter => {
        const active = filter.dataset.tagFilter === button.dataset.discoveryTag;
        filter.classList.toggle("active", active);
        filter.setAttribute("aria-pressed", String(active));
    });
    resetPageAndSearch({ revealFilters: true });
}));

document.querySelector("#clearFilters").addEventListener("click", () => {
    state.minPrice = ""; state.maxPrice = ""; state.region = ""; state.tags.clear(); state.sellerId = null;
    document.querySelector("#minPrice").value = "";
    document.querySelector("#maxPrice").value = "";
    document.querySelector("#searchRegion").value = "";
    document.querySelectorAll("[data-tag-filter]").forEach(button => { button.classList.remove("active"); button.setAttribute("aria-pressed", "false"); });
    resetPageAndSearch({ revealFilters: true });
});

userGrid.addEventListener("click", event => {
    const button = event.target.closest("[data-view-seller]");
    if (!button) return;
    state.scope = "ITEMS";
    state.sellerId = button.dataset.viewSeller;
    state.sort = "NEWEST";
    syncScopeUi();
    resetPageAndSearch({ revealFilters: true });
});

loadMoreButton.addEventListener("click", () => { state.page += 1; runSearch({ append: true, revealFilters: true }); });

syncScopeUi();
runSearch();
