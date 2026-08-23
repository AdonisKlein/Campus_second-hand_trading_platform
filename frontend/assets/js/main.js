const grid = document.querySelector("#itemGrid");
const itemCount = document.querySelector("#itemCount");
const searchForm = document.querySelector("#searchForm");

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
    grid.innerHTML = items.map(renderItem).join("");
}

function renderItem(item) {
    const image = item.imageUrl || "assets/images/placeholder.svg";
    return `
        <a class="item-card" href="detail.html?id=${encodeURIComponent(item.id)}">
            <img src="${escapeHtml(image)}" alt="${escapeHtml(item.title)}">
            <div class="item-card-body">
                <span class="tag">${escapeHtml(item.category)}</span>
                <h3>${escapeHtml(item.title)}</h3>
                <p class="price">￥${Number(item.price).toFixed(2)}</p>
                <p>${escapeHtml(item.description || "")}</p>
            </div>
        </a>
    `;
}

searchForm.addEventListener("submit", (event) => {
    event.preventDefault();
    loadItems({
        keyword: document.querySelector("#keyword").value.trim(),
        category: document.querySelector("#category").value
    });
});

loadItems();

