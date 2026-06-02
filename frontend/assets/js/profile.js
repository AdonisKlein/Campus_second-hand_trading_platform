const registerForm = document.querySelector("#registerForm");
const profileMessage = document.querySelector("#profileMessage");

registerForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const result = await request("/users/register", {
        method: "POST",
        body: JSON.stringify(formToJson(registerForm))
    });
    profileMessage.textContent = result.success ? `注册成功，用户ID：${result.data.id}` : result.message;
});

