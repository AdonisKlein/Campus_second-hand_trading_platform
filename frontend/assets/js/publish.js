const publishForm = document.querySelector("#publishForm");
const publishMessage = document.querySelector("#publishMessage");

function requireLoginForPublish() {
    const currentUser = getCurrentUser();
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
    const currentUser = requireLoginForPublish();
    if (!currentUser) {
        return;
    }

    const data = formToJson(publishForm);
    data.price = Number(data.price);
    data.sellerId = Number(currentUser.id);

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
