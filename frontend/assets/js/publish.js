const publishForm = document.querySelector("#publishForm");
const publishMessage = document.querySelector("#publishMessage");

publishForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = formToJson(publishForm);
    data.price = Number(data.price);
    data.sellerId = Number(data.sellerId);

    const result = await request("/items", {
        method: "POST",
        body: JSON.stringify(data)
    });

    publishMessage.textContent = result.success ? "发布成功" : result.message;
    if (result.success) {
        publishForm.reset();
    }
});

