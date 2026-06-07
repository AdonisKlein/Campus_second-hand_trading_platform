const publishForm = document.querySelector("#publishForm");
const publishMessage = document.querySelector("#publishMessage");

publishForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const currentUser = getCurrentUser();
    if (!currentUser) {
        publishMessage.textContent = '请先登录后再发布物品';
        setTimeout(() => { location.href = 'profile.html'; }, 800);
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
        publishForm.reset();
    }
});
