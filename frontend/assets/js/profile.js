const loginForm = document.querySelector("#loginForm");
const loginMessage = document.querySelector("#loginMessage");
const profileSection = document.querySelector("#profileSection");
const profileForm = document.querySelector("#profileForm");
const profileMessage = document.querySelector("#profileMessage");
const editProfileBtn = document.querySelector("#editProfileBtn");

function showLoggedInUI(user) {
    loginForm.style.display = "none";
    document.querySelector("#forgotPasswordForm").style.display = "none";
    profileSection.style.display = "block";
    document.querySelector("#viewUsername").textContent = user.username || "";
    document.querySelector("#viewNickname").textContent = user.nickname || "";
    document.querySelector("#viewPhone").textContent = user.phone || "";
    document.querySelector("#viewEmail").textContent = user.email || "";
    document.querySelector("#viewRegion").textContent = user.campusRegion || "未设置";
    document.querySelector("#viewCredit").textContent = user.creditScore ?? 100;
    profileForm.nickname.value = user.nickname || "";
    profileForm.phone.value = user.phone || "";
    profileForm.campusRegion.value = user.campusRegion || "学院路校区";
}

function showLoggedOutUI() {
    loginForm.style.display = "block";
    profileSection.style.display = "none";
}

(async function init() {
    const registrationMessage = sessionStorage.getItem("registrationMessage");
    if (registrationMessage) {
        loginMessage.textContent = registrationMessage;
        sessionStorage.removeItem("registrationMessage");
    }
    const user = await session.current();
    user ? showLoggedInUI(user) : showLoggedOutUI();
})();

loginForm.addEventListener("submit", async event => {
    event.preventDefault();
    const result = await session.login(loginForm.email.value.trim(), loginForm.password.value);
    if (result.success) {
        const target = consumePostLoginTarget();
        if (target) location.href = target;
        else showLoggedInUI(result.data);
    }
    else loginMessage.textContent = result.message || "登录失败";
});

editProfileBtn?.addEventListener("click", () => {
    document.querySelector("#profileView").style.display = "none";
    profileForm.style.display = "block";
    editProfileBtn.style.display = "none";
});

profileForm.addEventListener("submit", async event => {
    event.preventDefault();
    const data = formToJson(profileForm);
    delete data.email;
    delete data.emailCode;
    const result = await request("/users/me", { method: "PUT", body: JSON.stringify(data) });
    if (result.success) {
        session.set(result.data);
        showLoggedInUI(result.data);
        profileForm.style.display = "none";
        document.querySelector("#profileView").style.display = "";
        editProfileBtn.style.display = "";
        profileMessage.textContent = "保存成功";
    } else profileMessage.textContent = result.message || "保存失败";
});

document.querySelector(".profile-controls #logoutBtn")?.addEventListener("click", async () => {
    if (!confirm("确定要退出登录吗？")) return;
    await session.logout();
    showLoggedOutUI();
});

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
    event.preventDefault();
    loginForm.style.display = "none";
    forgotPasswordForm.style.display = "block";
});

fpBackToLogin?.addEventListener("click", event => {
    event.preventDefault();
    forgotPasswordForm.style.display = "none";
    loginForm.style.display = "block";
});

fpSendCodeBtn?.addEventListener("click", async () => {
    const email = fpEmail.value.trim();
    if (!/^\S+@\S+\.\S+$/.test(email)) {
        fpMessage.textContent = "请输入有效的邮箱地址";
        return;
    }
    const result = await request("/auth/verification/reset-password", {
        method: "POST", body: JSON.stringify({ email })
    });
    fpMessage.textContent = result.message || result.data;
    if (result.success) {
        fpStep2.style.display = "block";
        let remaining = 60;
        fpSendCodeBtn.disabled = true;
        clearInterval(fpTimer);
        fpTimer = setInterval(() => {
            fpCountdownEl.textContent = `请在 ${remaining--} 秒后重发`;
            if (remaining < 0) {
                clearInterval(fpTimer);
                fpSendCodeBtn.disabled = false;
                fpCountdownEl.textContent = "";
            }
        }, 1000);
    }
});

forgotPasswordForm?.addEventListener("submit", async event => {
    event.preventDefault();
    const data = {
        email: fpEmail.value.trim(),
        code: forgotPasswordForm.code.value.trim(),
        newPassword: forgotPasswordForm.newPassword.value
    };
    if (data.newPassword !== forgotPasswordForm.confirmPassword.value) {
        fpMessage.textContent = "两次输入的密码不一致";
        return;
    }
    const result = await request("/auth/password/reset", { method: "POST", body: JSON.stringify(data) });
    fpMessage.textContent = result.success ? "密码重置成功，请返回登录" : result.message;
});
