const { test, expect } = require('./fixtures/evidence');
const { waitForVerificationCode } = require('./fixtures/mailpit');
const { login, loginWithCredentials, sendVerificationCode } = require('./fixtures/app');

const onePixelPng = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64');

test('搜索→上传图片→发布→编辑→问答', async ({ page }) => {
  const suffix = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  const email = `e2e-product-${suffix}@example.com`;
  const username = `seller${suffix}`.slice(0, 50);
  const title = `E2E校园台灯${suffix}`;
  const editedTitle = `${title}已编辑`;

  await page.goto('/index.html');
  await page.locator('#keyword').fill(`不存在的关键词${suffix}`);
  await page.getByRole('button', { name: '搜索', exact: true }).click();
  await expect(page.locator('#searchFilters')).toBeVisible();
  await expect(page.locator('#resultTitle')).toContainText('不存在的关键词');

  await page.goto('/register.html');
  await page.getByLabel('用户名').fill(username);
  await page.getByLabel('邮箱').fill(email);
  await sendVerificationCode(page);
  await page.getByLabel('密码').fill('Aa123456');
  await page.getByLabel('验证码').fill(await waitForVerificationCode({ email }));
  await page.getByRole('button', { name: '注册', exact: true }).click();
  await expect(page).toHaveURL(/\/profile\.html$/);
  await loginWithCredentials(page, email, 'Aa123456');

  await page.goto('/publish.html');
  await page.getByLabel('标题').fill(title);
  await page.getByLabel('价格（元）').fill('25');
  await page.getByLabel('描述').fill('适合宿舍桌面使用，成色良好。');
  await page.locator('#publishImage').setInputFiles({ name: 'lamp.png', mimeType: 'image/png', buffer: onePixelPng });
  await page.getByRole('button', { name: '立即发布' }).click();
  await expect(page).toHaveURL(/\/my-items\.html$/, { timeout: 30_000 });
  await expect(page.locator('.inventory-card')).toContainText(title);

  const itemCard = page.locator('.inventory-card').filter({ hasText: title }).first();
  const itemId = await itemCard.getAttribute('data-item-id');
  await itemCard.getByRole('button', { name: '编辑资料' }).click();
  await expect(page.locator('#itemEditor')).toBeVisible();
  await page.locator('#itemEditorForm input[name="title"]').fill(editedTitle);
  await page.locator('#itemEditorForm textarea[name="description"]').fill('已编辑商品描述，支持当面验货。');
  await page.locator('#itemEditorForm').getByRole('button', { name: '保存修改' }).click();
  await expect(page.locator('#inventoryMessage')).toHaveText('商品资料已保存');
  await expect(page.locator('.inventory-card')).toContainText(editedTitle);

  await page.goto('/index.html');
  await page.locator('#keyword').fill(editedTitle);
  await page.getByRole('button', { name: '搜索', exact: true }).click();
  const searchResult = page.locator('.item-card').filter({ hasText: editedTitle }).first();
  await expect(searchResult).toBeVisible({ timeout: 15_000 });
  await searchResult.click();
  await expect(page.locator('#itemDetail h1')).toHaveText(editedTitle);

  await page.goto('/profile.html');
  page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: '退出登录' }).click();
  await expect(page.locator('#loginForm')).toBeVisible();
  await login(page, 'buyer');
  await page.goto(`/detail.html?id=${itemId}`);
  await expect.poll(() => page.evaluate(() => window.eval('currentItem')?.id ?? null), {
    message: '商品详情脚本应完成初始化'
  }).toBe(Number(itemId));
  await expect.poll(() => page.evaluate(async () => (await session.current({ refresh: true }))?.id ?? null), {
    message: '买家 Session 应在详情页恢复',
    timeout: 30_000,
    intervals: [500, 1_000, 2_000]
  }).toBe(2);
  const question = `请问这件商品可以在校内当面验货吗？${suffix}`;
  await page.locator('#publicQuestion').fill(question);
  const questionCreated = page.waitForResponse(response => response.url().endsWith('/api/messages')
    && response.request().method() === 'POST', { timeout: 30_000 });
  await page.locator('#messageForm').evaluate(form => form.requestSubmit());
  expect((await questionCreated).ok()).toBeTruthy();
  await expect(page.locator('#messageList')).toContainText(question, { timeout: 15_000 });
});

test('个人资料修改后公开资料更新', async ({ page }) => {
  await login(page, 'buyer');
  await page.locator('#profileSection #editProfileBtn').first().click();
  await page.locator('#profileForm input[name="nickname"]').fill(`新昵称${Date.now()}`);
  await page.locator('#profileForm select[name="campusRegion"]').selectOption('沙河校区');
  await page.locator('#profileForm button[type="submit"]').click();
  await expect(page.locator('#profileMessage')).toHaveText('资料已保存');
  await expect(page.locator('#viewRegion')).toHaveText('沙河校区');
});

test('卖家下架后可以重新上架', async ({ page }) => {
  await login(page, 'seller');
  const title = `E2E上下架商品${Date.now()}`;
  await page.goto('/publish.html');
  await page.getByLabel('标题').fill(title);
  await page.getByLabel('价格（元）').fill('12');
  await page.getByRole('button', { name: '立即发布' }).click();
  await expect(page).toHaveURL(/my-items\.html$/);
  const card = page.locator('.inventory-card').filter({ hasText: title }).first();
  page.once('dialog', dialog => dialog.accept());
  const withdrawn = page.waitForResponse(response => response.url().includes('/seller-actions')
    && response.request().method() === 'POST', { timeout: 30_000 });
  await card.locator('[data-inventory-action="WITHDRAW"]').click();
  expect((await withdrawn).ok()).toBeTruthy();
  await expect(page.locator('#inventoryMessage')).toHaveText('商品已下架', { timeout: 15_000 });
  page.once('dialog', dialog => dialog.accept());
  const relisted = page.waitForResponse(response => response.url().includes('/seller-actions')
    && response.request().method() === 'POST', { timeout: 30_000 });
  await page.locator('.inventory-card').filter({ hasText: title }).first().locator('[data-inventory-action="RELIST"]').click();
  expect((await relisted).ok()).toBeTruthy();
  await expect(page.locator('#inventoryMessage')).toHaveText('商品已重新上架', { timeout: 15_000 });
});
