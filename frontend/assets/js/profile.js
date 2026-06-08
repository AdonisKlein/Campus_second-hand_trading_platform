const loginForm = document.querySelector("#loginForm");
const loginMessage = document.querySelector("#loginMessage");
const profileSection = document.querySelector("#profileSection");
const profileForm = document.querySelector("#profileForm");
const profileMessage = document.querySelector("#profileMessage");
const editProfileBtn = document.querySelector("#editProfileBtn");
const sendEmailCodeBtn = document.querySelector("#sendEmailCodeBtn");
const emailCountdownEl = document.querySelector("#emailCountdown");
let emailCountdownTimer = null;

function showLoggedInUI(user) {
    loginForm.style.display = "none";
    profileSection.style.display = "block";
    document.querySelector("#viewUsername").textContent = user.username || "";
    document.querySelector("#viewNickname").textContent = user.nickname || "";
    document.querySelector("#viewPhone").textContent = user.phone || "";
    document.querySelector("#viewEmail").textContent = user.email || "";

    profileForm.nickname.value = user.nickname || "";
    profileForm.phone.value = user.phone || "";
    profileForm.email.value = user.email || "";
}

function showLoggedOutUI() {
    loginForm.style.display = "block";
    profileSection.style.display = "none";
}

(async function init() {
    const user = getCurrentUser();
    if (!user || !user.id) {
        showLoggedOutUI();
        return;
    }

    const res = await request(`/users/${user.id}`);
    if (res.success && res.data) {
        setCurrentUser(res.data);
        showLoggedInUI(res.data);
    } else {
        showLoggedInUI(user);
    }
})();

loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    loginMessage.textContent = "";
    const submitBtn = loginForm.querySelector("button[type='submit']");
    submitBtn.disabled = true;

    try {
        const res = await request("/users/login", {
            method: "POST",
            body: JSON.stringify(formToJson(loginForm))
        });

        if (res && res.success && res.data) {
            setCurrentUser(res.data);
            showLoggedInUI(res.data);
            return;
        }
        loginMessage.textContent = res && res.message ? res.message : "登录失败，请检查用户名和密码";
    } catch (error) {
        loginMessage.textContent = "登录时发生网络或服务器错误，请稍候再试";
    } finally {
        submitBtn.disabled = false;
    }
});

editProfileBtn?.addEventListener("click", () => {
    document.querySelector("#profileView").style.display = "none";
    profileForm.style.display = "block";
    editProfileBtn.style.display = "none";
});

profileForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    profileMessage.textContent = "";
    const user = getCurrentUser();
    if (!user || !user.id) {
        profileMessage.textContent = "未登录，请先登录";
        return;
    }

    const data = formToJson(profileForm);
    if (data.email && data.email !== (user.email || "")) {
        if (!/^\S+@\S+\.\S+$/.test(data.email)) {
            profileMessage.textContent = "请输入有效的邮箱地址";
            return;
        }

        const check = await request(`/users/check?email=${encodeURIComponent(data.email)}`);
        if (check && check.success && check.data && check.data.emailExists) {
            profileMessage.textContent = "该邮箱已被其它账号使用，请换一个邮箱";
            return;
        }
    }

    const res = await request(`/users/${user.id}`, {
        method: "PUT",
        body: JSON.stringify(data)
    });

    if (res.success && res.data) {
        setCurrentUser(res.data);
        showLoggedInUI(res.data);
        profileMessage.textContent = "保存成功";
        profileForm.style.display = "none";
        document.querySelector("#profileView").style.display = "";
        editProfileBtn.style.display = "";
    } else {
        profileMessage.textContent = res.message || "保存失败";
    }
});

function startEmailCountdown(seconds) {
    clearInterval(emailCountdownTimer);
    let remaining = seconds;
    sendEmailCodeBtn.style.display = "none";
    emailCountdownEl.textContent = `请在 ${remaining} 秒后重发`;
    emailCountdownTimer = setInterval(() => {
        remaining -= 1;
        if (remaining <= 0) {
            clearInterval(emailCountdownTimer);
            sendEmailCodeBtn.style.display = "";
            emailCountdownEl.textContent = "";
        } else {
            emailCountdownEl.textContent = `请在 ${remaining} 秒后重发`;
        }
    }, 1000);
}

sendEmailCodeBtn?.addEventListener("click", async () => {
    profileMessage.textContent = "";
    const email = profileForm.email.value.trim();
    if (!email) {
        profileMessage.textContent = "请输入要验证的新邮箱";
        return;
    }
    if (!/^\S+@\S+\.\S+$/.test(email)) {
        profileMessage.textContent = "请输入有效的邮箱地址";
        return;
    }

    const check = await request(`/users/check?email=${encodeURIComponent(email)}`);
    if (check && check.success && check.data && check.data.emailExists) {
        profileMessage.textContent = "该邮箱已被其它账号使用，请换一个邮箱";
        return;
    }

    const res = await request("/users/send-verification", {
        method: "POST",
        body: JSON.stringify({ email })
    });

    if (res && res.success) {
        profileMessage.textContent = "验证邮件已发送，请查看后端控制台或邮箱";
        startEmailCountdown(300);
    } else {
        profileMessage.textContent = res && res.message ? res.message : "发送失败";
    }
});

const confirmToast = document.querySelector("#confirmToast");
const confirmToastMessage = document.querySelector("#confirmToastMessage");
const confirmToastOk = document.querySelector("#confirmToastOk");
const confirmToastCancel = document.querySelector("#confirmToastCancel");

function showConfirmToast(message, onOk) {
    if (!confirmToast) {
        if (confirm(message)) {
            onOk();
        }
        return;
    }

    confirmToastMessage.textContent = message;
    confirmToast.style.display = "";

    function cleanup() {
        confirmToast.style.display = "none";
        confirmToastOk.removeEventListener("click", okHandler);
        confirmToastCancel.removeEventListener("click", cancelHandler);
    }

    function okHandler() {
        cleanup();
        onOk();
    }

    function cancelHandler() {
        cleanup();
    }

    confirmToastOk.addEventListener("click", okHandler);
    confirmToastCancel.addEventListener("click", cancelHandler);
}

document.querySelector(".profile-controls #logoutBtn")?.addEventListener("click", () => {
    showConfirmToast("确定要退出登录吗？", () => {
        clearCurrentUser();
        showLoggedOutUI();
    });
});

// ---------- Forgot Password ----------
const forgotPasswordLink = document.querySelector("#forgotPasswordLink");
const forgotPasswordForm = document.querySelector("#forgotPasswordForm");
const fpBackToLogin = document.querySelector("#fpBackToLogin");
const fpSendCodeBtn = document.querySelector("#fpSendCodeBtn");
const fpCountdownEl = document.querySelector("#fpCountdown");
const fpMessage = document.querySelector("#fpMessage");
const fpStep1 = document.querySelector("#fpStep1");
const fpStep2 = document.querySelector("#fpStep2");
const fpEmail = document.querySelector("#fpEmail");
let fpCountdownTimer = null;

// Show forgot password form, hide login form
forgotPasswordLink?.addEventListener("click", (e) => {
    e.preventDefault();
    loginForm.style.display = "none";
    forgotPasswordForm.style.display = "block";
    fpMessage.textContent = "";
    // Reset to step 1
    fpStep1.style.display = "block";
    fpStep2.style.display = "none";
    fpSendCodeBtn.disabled = false;
    fpEmail.value = "";
    forgotPasswordForm.email.value = "";
    forgotPasswordForm.code.value = "";
    forgotPasswordForm.newPassword.value = "";
    forgotPasswordForm.confirmPassword.value = "";
});

// Back to login
fpBackToLogin?.addEventListener("click", (e) => {
    e.preventDefault();
    forgotPasswordForm.style.display = "none";
    loginForm.style.display = "block";
    loginMessage.textContent = "";
    clearInterval(fpCountdownTimer);
    fpSendCodeBtn.disabled = false;
    fpSendCodeBtn.style.display = "";
    fpCountdownEl.textContent = "";
});

function startFpCountdown(seconds) {
    clearInterval(fpCountdownTimer);
    let remaining = seconds;
    fpSendCodeBtn.style.display = "none";
    fpCountdownEl.textContent = `请在 ${remaining} 秒后重发`;
    fpCountdownTimer = setInterval(() => {
        remaining -= 1;
        if (remaining <= 0) {
            clearInterval(fpCountdownTimer);
            fpSendCodeBtn.disabled = false;
            fpSendCodeBtn.style.display = "";
            fpCountdownEl.textContent = "";
        } else {
            fpCountdownEl.textContent = `请在 ${remaining} 秒后重发`;
        }
    }, 1000);
}

// Send verification code for forgot password
fpSendCodeBtn?.addEventListener("click", async () => {
    fpMessage.textContent = "";
    const email = fpEmail.value.trim();
    if (!email) {
        fpMessage.textContent = "请输入邮箱地址";
        return;
    }
    if (!/^\S+@\S+\.\S+$/.test(email)) {
        fpMessage.textContent = "请输入有效的邮箱地址";
        return;
    }

    fpSendCodeBtn.disabled = true;
    try {
        const res = await request("/users/forgot-password/send-code", {
            method: "POST",
            body: JSON.stringify({ email })
        });

        if (res && res.success) {
            fpMessage.textContent = "验证码已发送，请查看后端控制台或邮箱";
            startFpCountdown(300);
            // Show step 2
            fpStep2.style.display = "block";
        } else {
            fpMessage.textContent = res && res.message ? res.message : "发送失败，请稍候再试";
            fpSendCodeBtn.disabled = false;
        }
    } catch (err) {
        fpMessage.textContent = "发送验证码时发生错误，请稍候再试";
        fpSendCodeBtn.disabled = false;
    }
});

// Submit reset password
forgotPasswordForm?.addEventListener("submit", async (e) => {
    e.preventDefault();
    fpMessage.textContent = "";
    const submitBtn = forgotPasswordForm.querySelector("button[type='submit']");
    submitBtn.disabled = true;

    const data = {
        email: fpEmail.value.trim(),
        code: forgotPasswordForm.code.value.trim(),
        newPassword: forgotPasswordForm.newPassword.value
    };
    const confirmPassword = forgotPasswordForm.confirmPassword.value;

    // Client-side validation
    if (!data.email || !/^\S+@\S+\.\S+$/.test(data.email)) {
        fpMessage.textContent = "请输入有效的邮箱地址";
        submitBtn.disabled = false;
        return;
    }
    if (!data.code || !/^[0-9]{6}$/.test(data.code)) {
        fpMessage.textContent = "请输入6位数字验证码";
        submitBtn.disabled = false;
        return;
    }
    if (!/^((?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{6,12})$/.test(data.newPassword)) {
        fpMessage.textContent = "新密码需为6-12位，包含字母和数字";
        submitBtn.disabled = false;
        return;
    }
    if (data.newPassword !== confirmPassword) {
        fpMessage.textContent = "两次输入的密码不一致";
        submitBtn.disabled = false;
        return;
    }

    try {
        const res = await request("/users/forgot-password/reset", {
            method: "POST",
            body: JSON.stringify(data)
        });

        if (res && res.success) {
            fpMessage.textContent = "密码重置成功，请返回登录";
            // After 2 seconds, go back to login
            setTimeout(() => {
                forgotPasswordForm.style.display = "none";
                loginForm.style.display = "block";
                loginMessage.textContent = "密码已重置，请使用新密码登录";
            }, 2000);
        } else {
            fpMessage.textContent = res && res.message ? res.message : "重置失败，请稍候再试";
        }
    } catch (err) {
        fpMessage.textContent = "重置密码时发生错误，请稍候再试";
    } finally {
        submitBtn.disabled = false;
    }
});
