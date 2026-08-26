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
(async () => {
    const successMessage = sessionStorage.getItem("authSuccessMessage") || sessionStorage.getItem("registrationMessage");
    if (successMessage) {
        document.querySelector("#loginMessage").textContent = successMessage;
        sessionStorage.removeItem("authSuccessMessage");
        sessionStorage.removeItem("registrationMessage");
    }
    const user = await session.current(); render(user); if (user) stats();
})();
loginForm.addEventListener("submit", async event => { event.preventDefault(); const result = await session.login(loginForm.email.value.trim(), loginForm.password.value); if (result.success) { const target = consumePostLoginTarget(); if (target) location.href = target; else { render(result.data); stats(); } } else document.querySelector("#loginMessage").textContent = result.message || "登录失败"; });
document.querySelector("#editProfileBtn").addEventListener("click", () => edit(true)); document.querySelector("#cancelEditBtn").addEventListener("click", () => edit(false));
profileForm.addEventListener("submit", async event => { event.preventDefault(); const button = document.querySelector("#saveBtn"), message = document.querySelector("#profileMessage"); button.disabled = true; message.textContent = "正在保存…"; const result = await request("/users/me", { method: "PUT", body: JSON.stringify(formToJson(profileForm)) }); button.disabled = false; if (result.success) { session.set(result.data); render(result.data); edit(false); message.textContent = "资料已保存"; } else message.textContent = result.message || "保存失败，请稍后重试"; });
document.querySelector("#logoutBtn").addEventListener("click", async () => { await session.logout(); render(null); });

const forgotPasswordLink = document.querySelector("#forgotPasswordLink");
const forgotPasswordForm = document.querySelector("#forgotPasswordForm");
const fpBackToLogin = document.querySelector("#fpBackToLogin");
const fpSendCodeBtn = document.querySelector("#fpSendCodeBtn");
const fpCountdownEl = document.querySelector("#fpCountdown");
const fpMessage = document.querySelector("#fpMessage");
const fpStep2 = document.querySelector("#fpStep2");
const fpEmail = document.querySelector("#fpEmail");
let fpTimer = null;

forgotPasswordLink?.addEventListener("click", event => {
    event.preventDefault(); loginForm.hidden = true; forgotPasswordForm.style.display = "block";
});
fpBackToLogin?.addEventListener("click", event => {
    event.preventDefault(); forgotPasswordForm.style.display = "none"; loginForm.hidden = false;
});
fpSendCodeBtn?.addEventListener("click", async () => {
    const email = fpEmail.value.trim();
    if (!/^\S+@\S+\.\S+$/.test(email)) { fpMessage.textContent = "请输入有效的邮箱地址"; return; }
    const result = await request("/auth/verification/reset-password", { method: "POST", body: JSON.stringify({ email }) });
    fpMessage.textContent = result.message || result.data;
    if (result.success) {
        fpStep2.style.display = "block"; let remaining = 60; fpSendCodeBtn.disabled = true; clearInterval(fpTimer);
        fpTimer = setInterval(() => { fpCountdownEl.textContent = `请在 ${remaining--} 秒后重发`; if (remaining < 0) { clearInterval(fpTimer); fpSendCodeBtn.disabled = false; fpCountdownEl.textContent = ""; } }, 1000);
    }
});
forgotPasswordForm?.addEventListener("submit", async event => {
    event.preventDefault();
    const data = { email: fpEmail.value.trim(), code: forgotPasswordForm.code.value.trim(), newPassword: forgotPasswordForm.newPassword.value };
    if (data.newPassword !== forgotPasswordForm.confirmPassword.value) { fpMessage.textContent = "两次输入的密码不一致"; return; }
    const result = await request("/auth/password/reset", { method: "POST", body: JSON.stringify(data) });
    if (result.success) { redirectToLoginWithMessage("密码重置成功，请使用新密码登录"); return; }
    fpMessage.textContent = result.message || "密码重置失败，请稍后重试";
});
