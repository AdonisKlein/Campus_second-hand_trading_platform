const registerForm = document.querySelector('#registerForm');
const registerMessage = document.querySelector('#registerMessage');

registerForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    registerMessage.textContent = '';
    const data = formToJson(registerForm);
    const res = await request('/users/register', {
        method: 'POST',
        body: JSON.stringify(data)
    });
    if (res.success && res.data) {
        setCurrentUser(res.data);
        // redirect to profile page
        location.href = 'profile.html';
    } else {
        registerMessage.textContent = res.message || '注册失败';
    }
});

