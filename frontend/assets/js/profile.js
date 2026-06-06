const registerForm = document.querySelector("#registerForm");
const profileMessage = document.querySelector("#profileMessage");

registerForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const result = await request("/users/register", {
        method: "POST",
        body: JSON.stringify(formToJson(registerForm))
    });

    if (result.success) {
        localStorage.setItem("user", JSON.stringify(result.data));
        profileMessage.textContent = `注册成功，用户ID：${result.data.id}，已作为当前登录用户`;
    } else {
        profileMessage.textContent = result.message;
    }
});