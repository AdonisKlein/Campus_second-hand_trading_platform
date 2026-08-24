const grid = document.querySelector("#itemGrid");
const itemCount = document.querySelector("#itemCount");
const searchForm = document.querySelector("#searchForm");
const categorySelect = document.querySelector("#category");

function escapeHtml(value) {
    return String(value ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
}

async function loadItems(params = {}) {
    const query = new URLSearchParams(
        Object.entries(params).filter(([, value]) => value)
    ).toString();
    const result = await request(`/items${query ? `?${query}` : ""}`);
    const items = result.data || [];
    itemCount.textContent = `${items.length} 件物品`;
    grid.innerHTML = items.length
        ? items.map(renderItem).join("")
        : '<p class="empty-state">暂时没有符合条件的商品，换个关键词试试吧。</p>';
    installImageFallbacks(grid);
}

function renderItem(item) {
    const image = productImageUrl(item.imageUrl);
    const price = Number(item.price) === 0 ? "免费" : `￥${Number(item.price).toFixed(2)}`;
    return `
        <a class="item-card" href="detail.html?id=${encodeURIComponent(item.id)}">
            <div class="item-media"><img src="${escapeHtml(image)}" alt="${escapeHtml(item.title)}"><span class="tag">${escapeHtml(item.category)}</span></div>
            <div class="item-card-body">
                <h3>${escapeHtml(item.title)}</h3>
                <p class="item-description">${escapeHtml(item.description || "卖家暂未填写商品描述")}</p>
                <div class="item-seller"><span class="seller-avatar" aria-hidden="true">航</span><span>校园用户 #${escapeHtml(item.sellerId)}</span></div>
                <div class="item-card-footer"><p class="price">${price}</p><span>查看详情 →</span></div>
            </div>
        </a>
    `;
}

searchForm.addEventListener("submit", (event) => {
    event.preventDefault();
    loadItems({
        keyword: document.querySelector("#keyword").value.trim(),
        category: categorySelect.value
    });
});

document.querySelectorAll("[data-category-filter]").forEach(button => {
    button.addEventListener("click", () => {
        const category = button.dataset.categoryFilter;
        categorySelect.value = category;
        document.querySelector("#keyword").value = "";
        document.querySelectorAll("[data-category-filter]").forEach(candidate => {
            candidate.classList.toggle("active", candidate.dataset.categoryFilter === category);
        });
        loadItems({ category });
    });
});

loadItems();

