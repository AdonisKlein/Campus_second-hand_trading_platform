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
