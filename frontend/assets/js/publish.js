// 检查是否登录
const currentUser = localStorage.getItem('user');
if (!currentUser) {
    alert('请先登录再发布商品');
    window.location.href = 'login.html';
}

const publishForm = document.querySelector("#publishForm");
const publishMessage = document.querySelector("#publishMessage");

publishForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = formToJson(publishForm);
    data.price = Number(data.price);
    // 自动使用当前登录用户的ID
    data.sellerId = JSON.parse(currentUser).id;

    const result = await request("/items", {
        method: "POST",
        body: JSON.stringify(data)
    });

    publishMessage.textContent = result.success ? "发布成功" : result.message;
    if (result.success) {
        alert('发布成功！');
        window.location.href = 'index.html';
    }
});
