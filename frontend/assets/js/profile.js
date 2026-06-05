const loginForm = document.querySelector("#loginForm");
const loginMessage = document.querySelector("#loginMessage");
const profileSection = document.querySelector("#profileSection");
const profileView = document.querySelector("#profileView");
const profileForm = document.querySelector("#profileForm");
const profileMessage = document.querySelector("#profileMessage");
const logoutBtn = document.querySelector("#logoutBtn");
const editProfileBtn = document.querySelector('#editProfileBtn');
const saveBtn = document.querySelector('#saveBtn');
const sendEmailCodeBtn = document.querySelector('#sendEmailCodeBtn');
const emailCountdownEl = document.querySelector('#emailCountdown');

let emailCountdownTimer = null;

function showLoggedInUI(user) {
    document.querySelector('#loginForm').style.display = 'none';
    profileSection.style.display = 'block';
    document.querySelector('#viewUsername').textContent = user.username || '';
    document.querySelector('#viewNickname').textContent = user.nickname || '';
    document.querySelector('#viewPhone').textContent = user.phone || '';
    document.querySelector('#viewEmail').textContent = user.email || '';

    // fill edit form
    profileForm.nickname.value = user.nickname || '';
    profileForm.phone.value = user.phone || '';
    profileForm.email.value = user.email || '';
}

function showLoggedOutUI() {
    document.querySelector('#loginForm').style.display = 'block';
    profileSection.style.display = 'none';
}

// On load, check current user
(async function init() {
    const user = getCurrentUser();
    if (user) {
        // fetch latest profile from backend
        const res = await request(`/users/${user.id}`);
        if (res.success && res.data) {
            setCurrentUser(res.data);
            showLoggedInUI(res.data);
        } else {
            // fallback show from local
            showLoggedInUI(user);
        }
    } else {
        showLoggedOutUI();
    }
})();

// Login only
loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    loginMessage.textContent = '';
    const submitBtn = loginForm.querySelector('button[type="submit"]');
    submitBtn.disabled = true;
    const data = formToJson(loginForm);
    try {
        const res = await request('/users/login', {
            method: 'POST',
            body: JSON.stringify(data)
        });
        if (res && res.success && res.data) {
            setCurrentUser(res.data);
            showLoggedInUI(res.data);
            return;
        }
        loginMessage.textContent = res && res.message ? res.message : '登录失败，请检查用户名和密码';
    } catch (err) {
        loginMessage.textContent = '登录时发生网络或服务器错误，请稍候再试';
    } finally {
        submitBtn.disabled = false;
    }
});

// when clicking modify, show edit form
editProfileBtn?.addEventListener('click', (e) => {
    document.getElementById('profileView').style.display = 'none';
    document.getElementById('profileForm').style.display = 'block';
    editProfileBtn.style.display = 'none';
});

// Update profile
profileForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    profileMessage.textContent = '';
    const user = getCurrentUser();
    if (!user) {
        profileMessage.textContent = '未登录，请先登录';
        return;
    }
    const data = formToJson(profileForm);

    // If email changed, validate uniqueness
    if (data.email && data.email !== (user.email || '')) {
        // basic email format check
        if (!/^\S+@\S+\.\S+$/.test(data.email)) {
            profileMessage.textContent = '请输入有效的邮箱地址';
            return;
        }
        try {
            const check = await request(`/users/check?email=${encodeURIComponent(data.email)}`);
            if (check && check.success && check.data && check.data.emailExists) {
                profileMessage.textContent = '该邮箱已被其它账号使用，请换一个邮箱';
                return;
            }
        } catch (err) {
            // ignore check error and proceed
        }
    }

    const res = await request(`/users/${user.id}`, {
        method: 'PUT',
        body: JSON.stringify(data)
    });
    if (res.success && res.data) {
        setCurrentUser(res.data);
        // update view
        showLoggedInUI(res.data);
        profileMessage.textContent = '保存成功';
        // hide form
        document.getElementById('profileForm').style.display = 'none';
        document.getElementById('profileView').style.display = '';
        editProfileBtn.style.display = '';
    } else {
        profileMessage.textContent = res.message || '保存失败';
    }
});

// Email verification code handling
function startEmailCountdown(seconds) {
    clearInterval(emailCountdownTimer);
    let remaining = seconds;
    sendEmailCodeBtn.style.display = 'none';
    emailCountdownEl.textContent = `请在 ${remaining} 秒后重发`;
    emailCountdownTimer = setInterval(() => {
        remaining -= 1;
        if (remaining <= 0) {
            clearInterval(emailCountdownTimer);
            sendEmailCodeBtn.style.display = '';
            emailCountdownEl.textContent = '';
        } else {
            emailCountdownEl.textContent = `请在 ${remaining} 秒后重发`;
        }
    }, 1000);
}

sendEmailCodeBtn?.addEventListener('click', async (e) => {
    profileMessage.textContent = '';
    const email = profileForm.email.value.trim();
    if (!email) {
        profileMessage.textContent = '请输入要验证的新邮箱';
        return;
    }
    if (!/^\S+@\S+\.\S+$/.test(email)) {
        profileMessage.textContent = '请输入有效的邮箱地址';
        return;
    }
    // check uniqueness
    try {
        const check = await request(`/users/check?email=${encodeURIComponent(email)}`);
        if (check && check.success && check.data && check.data.emailExists) {
            profileMessage.textContent = '该邮箱已被其它账号使用，请换一个邮箱';
            return;
        }
    } catch (err) {
        // ignore
    }
    // send verification
    try {
        const res = await request('/users/send-verification', {
            method: 'POST',
            body: JSON.stringify({ email })
        });
        if (res && res.success) {
            profileMessage.textContent = '验证邮件已发送，请查看后端控制台或邮箱';
            startEmailCountdown(300);
        } else {
            profileMessage.textContent = res && res.message ? res.message : '发送失败';
        }
    } catch (err) {
        profileMessage.textContent = '发送失败，请稍候再试';
    }
});

// Logout with toast confirmation
const confirmToast = document.getElementById('confirmToast');
const confirmToastMessage = document.getElementById('confirmToastMessage');
const confirmToastOk = document.getElementById('confirmToastOk');
const confirmToastCancel = document.getElementById('confirmToastCancel');

function showConfirmToast(message, onOk) {
    if (!confirmToast) {
        // fallback to browser confirm
        if (confirm(message)) onOk();
        return;
    }
    confirmToastMessage.textContent = message;
    confirmToast.style.display = '';

    function cleanup() {
        confirmToast.style.display = 'none';
        confirmToastOk.removeEventListener('click', okHandler);
        confirmToastCancel.removeEventListener('click', cancelHandler);
    }
    function okHandler() { cleanup(); onOk(); }
    function cancelHandler() { cleanup(); }
    confirmToastOk.addEventListener('click', okHandler);
    confirmToastCancel.addEventListener('click', cancelHandler);
}

const logoutControlBtn2 = document.querySelector('.profile-controls #logoutBtn');
if (logoutControlBtn2) {
    logoutControlBtn2.addEventListener('click', (e) => {
        showConfirmToast('确定要退出登录吗？', () => {
            clearCurrentUser();
            showLoggedOutUI();
        });
    });
}
