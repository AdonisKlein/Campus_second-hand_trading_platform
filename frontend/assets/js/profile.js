const loginForm = document.querySelector("#loginForm");
const profileSection = document.querySelector("#profileSection");
const profileForm = document.querySelector("#profileForm");
const text = (id, value) => { const el = document.querySelector(`#${id}`); if (el) el.textContent = value ?? ""; };
function render(user) {
    loginForm.hidden = Boolean(user); profileSection.hidden = !user; if (!user) return;
    const name = user.nickname || user.username || "校园用户";
    text("profileName", name); text("profileAvatar", name.slice(0, 1)); text("profileSubline", `${user.username || ""} · ${user.email || ""}`);
    text("viewNickname", user.nickname || "未设置"); text("viewPhone", user.phone || "未设置"); text("viewEmail", user.email || ""); text("viewRegion", user.campusRegion || "未设置"); text("viewCredit", user.creditScore ?? 100); text("viewActive", user.lastActiveAt ? new Date(user.lastActiveAt).toLocaleDateString("zh-CN") : "刚刚");
    profileForm.nickname.value = user.nickname || ""; profileForm.phone.value = user.phone || ""; profileForm.campusRegion.value = user.campusRegion || "学院路校区";
}
async function stats() {
    const [items, buying, selling, unread] = await Promise.all([request("/items/mine"), request("/orders/desk?perspective=BUYING&stage=ALL"), request("/orders/desk?perspective=SELLING&stage=ALL"), request("/chat/unread-count")]);
    text("statItems", items.success ? (items.data || []).filter(i => i.status === "ON_SALE").length : "-");
    text("statBuying", buying.success ? buying.data?.summary?.requests ?? buying.data?.entries?.length ?? 0 : "-");
    text("statSelling", selling.success ? selling.data?.summary?.actionable ?? selling.data?.entries?.length ?? 0 : "-");
    text("statUnread", unread.success ? unread.data?.count || 0 : "-");
}
function edit(on) { document.querySelector("#profileView").hidden = on; profileForm.hidden = !on; document.querySelector("#editProfileBtn").hidden = on; }
(async () => { const user = await session.current(); render(user); if (user) stats(); })();
loginForm.addEventListener("submit", async event => { event.preventDefault(); const result = await session.login(loginForm.email.value.trim(), loginForm.password.value); if (result.success) { const target = consumePostLoginTarget(); if (target) location.href = target; else { render(result.data); stats(); } } else document.querySelector("#loginMessage").textContent = result.message || "登录失败"; });
document.querySelector("#editProfileBtn").addEventListener("click", () => edit(true)); document.querySelector("#cancelEditBtn").addEventListener("click", () => edit(false));
profileForm.addEventListener("submit", async event => { event.preventDefault(); const button = document.querySelector("#saveBtn"), message = document.querySelector("#profileMessage"); button.disabled = true; message.textContent = "正在保存…"; const result = await request("/users/me", { method: "PUT", body: JSON.stringify(formToJson(profileForm)) }); button.disabled = false; if (result.success) { session.set(result.data); render(result.data); edit(false); message.textContent = "资料已保存"; } else message.textContent = result.message || "保存失败，请稍后重试"; });
document.querySelector("#logoutBtn").addEventListener("click", async () => { await session.logout(); render(null); });
