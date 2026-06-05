const registerForm = document.querySelector('#registerForm');
const registerMessage = document.querySelector('#registerMessage');
const sendCodeBtn = document.querySelector('#sendCodeBtn');
const countdownEl = document.querySelector('#countdown');
let countdownTimer = null;
let countdownRemaining = 0;

function startCountdown(seconds) {
    clearInterval(countdownTimer);
    countdownRemaining = seconds;
    // hide send button and show countdown text
    sendCodeBtn.style.display = 'none';
    updateCountdown();
    countdownTimer = setInterval(() => {
        countdownRemaining -= 1;
        if (countdownRemaining <= 0) {
            clearInterval(countdownTimer);
            // show send button again
            sendCodeBtn.style.display = '';
            countdownEl.textContent = '';
        } else {
            updateCountdown();
        }
    }, 1000);
}

function updateCountdown() {
    countdownEl.textContent = `请在 ${countdownRemaining} 秒后重发`;
}

async function preCheckUsernameEmail(username, email) {
    const res = await request(`/users/check?username=${encodeURIComponent(username)}&email=${encodeURIComponent(email)}`);
    return res;
}

sendCodeBtn.addEventListener('click', async () => {
    const email = registerForm.email.value.trim();
    const username = registerForm.username.value.trim();
    registerMessage.textContent = '';
    if (!email) {
        registerMessage.textContent = '请输入邮箱地址';
        return;
    }
    if (!username) {
        registerMessage.textContent = '请先填写用户名';
        return;
    }
    // basic email format check
    if (!/^\S+@\S+\.\S+$/.test(email)) {
        registerMessage.textContent = '请输入有效的邮箱地址';
        return;
    }

    // Pre-check username/email existence
    try {
        const check = await preCheckUsernameEmail(username, email);
        if (check && check.success && check.data) {
            const { usernameExists, emailExists } = check.data;
            if (usernameExists) {
                registerMessage.textContent = '用户名已存在，请换一个用户名';
                return;
            }
            if (emailExists) {
                registerMessage.textContent = '该邮箱已被注册，请使用其他邮箱或直接登录';
                return;
            }
        }
    } catch (err) {
        // ignore check error and proceed
    }

    sendCodeBtn.disabled = true;
    try {
        const res = await request('/users/send-verification', {
            method: 'POST',
            body: JSON.stringify({ email })
        });
        if (res && res.success) {
            registerMessage.textContent = '验证码已发送，请查看后端控制台或邮箱';
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

function validateRegisterInput(data) {
    if (!data.username || data.username.trim().length === 0) return '请输入用户名';
    // password: 6-12 letters+digits (same as backend regex)
    if (!/^((?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{6,12})$/.test(data.password)) return '密码需为6-12位，包含字母和数字';
    if (!/^\S+@\S+\.\S+$/.test(data.email)) return '请输入有效的邮箱地址';
    if (!data.code || !/^[0-9]{6}$/.test(data.code)) return '请输入6位数字验证码';
    return null;
}

registerForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    registerMessage.textContent = '';
    const submitBtn = registerForm.querySelector('button[type="submit"]');
    submitBtn.disabled = true;
    const data = formToJson(registerForm);

    const clientValidationError = validateRegisterInput(data);
    if (clientValidationError) {
        registerMessage.textContent = clientValidationError;
        submitBtn.disabled = false;
        return;
    }

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
