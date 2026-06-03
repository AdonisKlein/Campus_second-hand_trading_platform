const loginForm = document.querySelector("#loginForm");
const loginMessage = document.querySelector("#loginMessage");
const registerForm = document.querySelector("#registerForm");
const registerMessage = document.querySelector("#registerMessage");
const profileSection = document.querySelector("#profileSection");
const profileView = document.querySelector("#profileView");
const profileForm = document.querySelector("#profileForm");
const profileMessage = document.querySelector("#profileMessage");
const logoutBtn = document.querySelector("#logoutBtn");

function showLoggedInUI(user) {
    document.querySelector('#loginForm').style.display = 'none';
    document.querySelector('#registerForm').style.display = 'none';
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
    document.querySelector('#registerForm').style.display = 'block';
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

// Login
loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    loginMessage.textContent = '';
    const data = formToJson(loginForm);
    const res = await request('/users/login', {
        method: 'POST',
        body: JSON.stringify(data)
    });
    if (res.success && res.data) {
        setCurrentUser(res.data);
        showLoggedInUI(res.data);
    } else {
        loginMessage.textContent = res.message || '登录失败';
    }
});

// Register (keep existing behavior but after success, optionally auto-login)
registerForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    registerMessage.textContent = '';
    const data = formToJson(registerForm);
    const res = await request('/users/register', {
        method: 'POST',
        body: JSON.stringify(data)
    });
    if (res.success && res.data) {
        registerMessage.textContent = '注册成功，已为您登录';
        // auto-login by setting current user
        setCurrentUser(res.data);
        showLoggedInUI(res.data);
    } else {
        registerMessage.textContent = res.message || '注册失败';
    }
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
    const res = await request(`/users/${user.id}`, {
        method: 'PUT',
        body: JSON.stringify(data)
    });
    if (res.success && res.data) {
        setCurrentUser(res.data);
        showLoggedInUI(res.data);
        profileMessage.textContent = '保存成功';
    } else {
        profileMessage.textContent = res.message || '保存失败';
    }
});

// Logout
logoutBtn.addEventListener('click', (e) => {
    clearCurrentUser();
    showLoggedOutUI();
});
