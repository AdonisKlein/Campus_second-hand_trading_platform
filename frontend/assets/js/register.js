const registerForm = document.querySelector('#registerForm');
const registerMessage = document.querySelector('#registerMessage');
const sendCodeBtn = document.querySelector('#sendCodeBtn');
const countdownEl = document.querySelector('#countdown');
let countdownTimer = null;
let countdownRemaining = 0;

function startCountdown(seconds) {
    clearInterval(countdownTimer);
    countdownRemaining = seconds;
    sendCodeBtn.disabled = true;
    updateCountdown();
    countdownTimer = setInterval(() => {
        countdownRemaining -= 1;
        if (countdownRemaining <= 0) {
            clearInterval(countdownTimer);
            sendCodeBtn.disabled = false;
            countdownEl.textContent = '';
        } else {
            updateCountdown();
        }
    }, 1000);
}

function updateCountdown() {
    countdownEl.textContent = `请在 ${countdownRemaining} 秒后重发`;
}

sendCodeBtn.addEventListener('click', async () => {
    const email = registerForm.email.value.trim();
    registerMessage.textContent = '';
    if (!email) {
        registerMessage.textContent = '请输入邮箱地址';
        return;
    }
    // basic email format check
    if (!/^\S+@\S+\.\S+$/.test(email)) {
        registerMessage.textContent = '请输入有效的邮箱地址';
        return;
    }
    sendCodeBtn.disabled = true;
    try {
        const res = await request('/users/send-verification', {
            method: 'POST',
            body: JSON.stringify({ email })
        });
        if (res && res.success) {
            registerMessage.textContent = '验证码已发送，请查看邮箱（开发环境会在后端控制台打印）';
            startCountdown(300);
        } else {
            registerMessage.textContent = res && res.message ? res.message : '发送失败，请稍候再试';
            sendCodeBtn.disabled = false;
        }
    } catch (err) {
        registerMessage.textContent = '发送验证码时发生错误，请稍候再试';
        sendCodeBtn.disabled = false;
    }
});

registerForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    registerMessage.textContent = '';
    const submitBtn = registerForm.querySelector('button[type="submit"]');
    submitBtn.disabled = true;
    const data = formToJson(registerForm);
    try {
        const res = await request('/users/register', {
            method: 'POST',
            body: JSON.stringify(data)
        });
        if (res && res.success && res.data) {
            setCurrentUser(res.data);
            location.href = 'profile.html';
            return;
        }
        registerMessage.textContent = res && res.message ? res.message : '注册失败，请检查输入';
    } catch (err) {
        registerMessage.textContent = '注册时发生错误，请稍候再试';
    } finally {
        submitBtn.disabled = false;
    }
});
