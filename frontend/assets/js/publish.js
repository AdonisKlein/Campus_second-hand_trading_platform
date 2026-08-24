const publishForm = document.querySelector("#publishForm");
const publishMessage = document.querySelector("#publishMessage");
const publishImage = document.querySelector("#publishImage");
const publishImagePreview = document.querySelector("#publishImagePreview");
let previewObjectUrl = null;
installImageFallbacks(document);

publishImage.addEventListener("change", () => {
    if (previewObjectUrl) URL.revokeObjectURL(previewObjectUrl);
    const file = publishImage.files[0];
    const error = validateProductImageFile(file);
    if (error) {
        publishMessage.textContent = error;
        publishImage.value = "";
    }
    previewObjectUrl = file && !error ? URL.createObjectURL(file) : null;
    publishImagePreview.src = previewObjectUrl || "assets/images/placeholder.svg";
});

async function requireLoginForPublish() {
    return requireAuthenticatedUser({ message: "登录后才能发布闲置，是否前往登录？", returnTo: "publish.html" });
}

requireLoginForPublish();

publishForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const submitButton = publishForm.querySelector('button[type="submit"]');
    submitButton.disabled = true;
    const currentUser = await requireLoginForPublish();
    if (!currentUser) {
        submitButton.disabled = false;
        return;
    }

    const data = formToJson(publishForm);
    delete data.imageFile;
    data.price = Number(data.price);
    data.tags = [...publishForm.querySelectorAll('input[name="tags"]:checked')].map(input => input.value);
    if (data.tags.length > 4) {
        publishMessage.textContent = "商品标签最多选择 4 个";
        submitButton.disabled = false;
        return;
    }

    const imageFile = publishImage.files[0];
    const imageError = validateProductImageFile(imageFile);
    if (imageError) {
        publishMessage.textContent = imageError;
        submitButton.disabled = false;
        return;
    }
    if (imageFile) {
        publishForm.setAttribute("aria-busy", "true");
        submitButton.textContent = "正在上传…";
        publishMessage.textContent = "正在安全处理图片…";
        const upload = await uploadProductImage(imageFile);
        if (!upload.success) {
            publishMessage.textContent = upload.message || "图片上传失败";
            submitButton.disabled = false;
            submitButton.textContent = "立即发布";
            publishForm.removeAttribute("aria-busy");
            return;
        }
        data.imageUrl = upload.data.url;
    } else {
        data.imageUrl = null;
    }

    publishForm.setAttribute("aria-busy", "true");
    submitButton.textContent = "正在发布…";

    const result = await request("/items", {
        method: "POST",
        body: JSON.stringify(data)
    });

    publishMessage.textContent = result.success ? "发布成功" : result.message;
    if (result.success) {
        location.href = "my-items.html";
    } else {
        submitButton.disabled = false;
        submitButton.textContent = "立即发布";
        publishForm.removeAttribute("aria-busy");
    }
});
