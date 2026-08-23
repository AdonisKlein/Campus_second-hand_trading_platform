const publishForm = document.querySelector("#publishForm");
const publishMessage = document.querySelector("#publishMessage");

async function requireLoginForPublish() {
    const currentUser = await session.current();
    if (!currentUser || !currentUser.id) {
        publishMessage.textContent = "请先登录后再发布物品";
        setTimeout(() => {
            location.href = "profile.html";
        }, 800);
        return null;
    }
    return currentUser;
}

requireLoginForPublish();

publishForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const currentUser = await requireLoginForPublish();
    if (!currentUser) {
        return;
    }

    const data = formToJson(publishForm);
    data.price = Number(data.price);

    const result = await request("/items", {
        method: "POST",
        body: JSON.stringify(data)
    });

    publishMessage.textContent = result.success ? "发布成功" : result.message;
    if (result.success) {
        alert("发布成功！");
        location.href = "index.html";
    }
});
