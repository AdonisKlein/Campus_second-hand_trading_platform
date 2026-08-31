const { test, expect } = require('../fixtures/evidence');
const { ACCOUNTS, api, login, publishItem } = require('../fixtures/app');

function uniqueTitle(prefix) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
}

async function acceptDialogOnClick(page, locator, promptText) {
  const handled = page.waitForEvent('dialog').then(dialog => dialog.accept(promptText));
  await Promise.all([handled, locator.click()]);
}

async function waitForOrderText(page, orderId, expected, perspective = 'BUYING') {
  await expect.poll(async () => {
    await page.reload();
    await page.locator(`[data-perspective="${perspective}"]`).click();
    return page.locator(`[data-order-record="${orderId}"]`).textContent();
  }, { timeout: 20_000, intervals: [500, 1_000, 2_000] }).toContain(expected);
}

async function submitOrderAction(page, order, orderId, action) {
  await order.locator(`[data-order-action="${action}"]`).click();
  const actionCompleted = page.waitForResponse(response => response.url().endsWith(`/api/orders/${orderId}/actions`)
    && response.request().method() === 'POST');
  await page.locator('#orderActionDialog [data-confirm-action]').click();
  expect((await actionCompleted).status()).toBe(200);
  await expect(page.locator('#orderActionDialog')).not.toBeVisible();
}

test('私聊→未读→屏蔽', async ({ page: buyer, browser }) => {
  test.setTimeout(120_000);
  const sellerContext = await browser.newContext();
  const seller = await sellerContext.newPage();
  const message = `E2E 私聊未读消息 ${Date.now()}`;
  try {
    await login(seller, 'seller');
    const item = await publishItem(seller, uniqueTitle('私聊商品'));
    await login(buyer, 'buyer');
    await buyer.goto(`/detail.html?id=${item.id}`);
    await expect.poll(() => buyer.evaluate(async () => (await session.current({ refresh: true }))?.id ?? null), {
      message: '买家 Session 应在商品详情页恢复',
      timeout: 30_000,
      intervals: [500, 1_000, 2_000]
    }).toBe(2);
    const chatButton = buyer.locator('button[data-product-action="chat"]').first();
    await expect(chatButton).toBeEnabled();
    const conversationCreated = buyer.waitForResponse(response => response.url().endsWith('/api/chat/conversations')
      && response.request().method() === 'POST');
    await chatButton.click();
    expect((await conversationCreated).ok()).toBeTruthy();
    await expect(buyer).toHaveURL(/messages\.html\?conversation=/);
    await expect(buyer.locator('#chatRoom')).toBeVisible();
    await buyer.locator('#chatBody').fill(message);
    await buyer.locator('#chatForm button[type="submit"]').click();
    await expect(buyer.locator('#chatMessages')).toContainText(message);

    await seller.goto('/messages.html');
    await expect(seller.locator('#chatTotalUnread')).toContainText('1 条未读');
    const conversation = seller.locator('.conversation-card').filter({ hasText: item.title }).first();
    await expect(conversation.locator('em')).toHaveText('1');
    await conversation.click();
    await expect(seller.locator('#chatMessages')).toContainText(message);
    const blocked = seller.waitForResponse(response => response.url().includes('/api/chat/blocks/')
      && response.request().method() === 'PUT');
    await seller.locator('#toggleChatBlock').click();
    expect((await blocked).ok()).toBeTruthy();
    await expect(seller.locator('#toggleChatBlock')).toHaveText('解除屏蔽', { timeout: 15_000 });
    await expect(seller.locator('#chatBody')).toBeDisabled();
    const unblocked = seller.waitForResponse(response => response.url().includes('/api/chat/blocks/')
      && response.request().method() === 'DELETE');
    await seller.locator('#toggleChatBlock').click();
    expect((await unblocked).ok()).toBeTruthy();
    await expect(seller.locator('#toggleChatBlock')).toHaveText('屏蔽', { timeout: 15_000 });
  } finally {
    await sellerContext.close();
  }
});

test('购买意向→卖家选择→交接完成', async ({ page: buyer, browser }) => {
  const sellerContext = await browser.newContext();
  const seller = await sellerContext.newPage();
  try {
    await login(seller, 'seller');
    const item = await publishItem(seller, uniqueTitle('交易商品'));
    await login(buyer, 'buyer');
    await buyer.goto(`/detail.html?id=${item.id}`);
    const requestCreated = buyer.waitForResponse(response => response.url().endsWith('/api/orders')
      && response.request().method() === 'POST');
    await buyer.locator('button[data-product-action="request"]').first().click();
    expect((await requestCreated).status()).toBe(200);
    await expect(buyer.locator('.purchase-request-notice')).toContainText('购买意向已提交', { timeout: 15_000 });
    await buyer.goto('/orders.html');
    const buyerGroup = buyer.locator('.order-item-group').filter({ hasText: item.title }).first();
    const buyerOrder = buyerGroup.locator('.order-record').first();
    await expect(buyerOrder).toContainText('等待卖家回应');
    const orderId = await buyerOrder.getAttribute('data-order-record');
    expect(orderId).toMatch(/^\d+$/);

    await seller.goto('/orders.html');
    await seller.locator('[data-perspective="SELLING"]').click();
    const sellerOrder = seller.locator(`[data-order-record="${orderId}"]`);
    await expect(sellerOrder).toBeVisible();
    await submitOrderAction(seller, sellerOrder, orderId, 'ACCEPT');
    await waitForOrderText(seller, orderId, '待当面交易', 'SELLING');

    await buyer.reload();
    const selectedOrder = buyer.locator(`[data-order-record="${orderId}"]`);
    await expect(selectedOrder).toContainText('待当面交易');
    await submitOrderAction(buyer, selectedOrder, orderId, 'COMPLETE');
    await waitForOrderText(buyer, orderId, '交易完成');
  } finally {
    await sellerContext.close();
  }
});

test('订单工作台→买卖视角→阶段筛选→时间线与动作集合', async ({ page: buyer, browser }) => {
  const sellerContext = await browser.newContext();
  const seller = await sellerContext.newPage();
  try {
    await login(seller, 'seller');
    const item = await publishItem(seller, uniqueTitle('工作台商品'));
    await login(buyer, 'buyer');
    await buyer.goto(`/detail.html?id=${item.id}`);
    const requestCreated = buyer.waitForResponse(response => response.url().endsWith('/api/orders')
      && response.request().method() === 'POST');
    await buyer.locator('button[data-product-action="request"]').first().click();
    expect((await requestCreated).status()).toBe(200);
    await expect(buyer.locator('.purchase-request-notice')).toContainText('购买意向已提交', { timeout: 15_000 });

    await buyer.goto('/orders.html');
    const buyerGroup = buyer.locator('.order-item-group').filter({ hasText: item.title }).first();
    const buyerOrder = buyerGroup.locator('.order-record').first();
    await expect(buyerOrder).toContainText('等待卖家回应');
    await expect(buyerOrder.locator('.order-record-actions')).toContainText('取消交易');
    await buyer.locator('[data-stage="REQUESTS"]').click();
    await expect(buyer.locator('#orderGroups')).toContainText(item.title);

    const orderId = await buyerOrder.getAttribute('data-order-record');
    await seller.goto('/orders.html');
    await seller.locator('[data-perspective="SELLING"]').click();
    const sellerOrder = seller.locator(`[data-order-record="${orderId}"]`);
    await expect(sellerOrder).toBeVisible();
    await expect(sellerOrder.locator('.order-record-actions')).toContainText('接受');
    await submitOrderAction(seller, sellerOrder, orderId, 'ACCEPT');
    await waitForOrderText(seller, orderId, '待当面交易', 'SELLING');
    await expect(seller.locator(`[data-order-record="${orderId}"] .order-mini-timeline`)).toBeVisible();

    await buyer.reload();
    await expect(buyer.locator(`[data-order-record="${orderId}"]`)).toContainText('待当面交易');
    await buyer.locator('[data-stage="HANDOVER"]').click();
    await expect(buyer.locator('#orderGroups')).toContainText(item.title);
    await expect(buyer.locator(`[data-order-record="${orderId}"] .order-record-actions`)).toContainText('确认已取货');
  } finally {
    await sellerContext.close();
  }
});

test('管理员举报治理及用户管理', async ({ page: admin, browser }) => {
  test.setTimeout(120_000);
  const sellerContext = await browser.newContext();
  const buyerContext = await browser.newContext();
  const seller = await sellerContext.newPage();
  const buyer = await buyerContext.newPage();
  try {
    await login(seller, 'seller');
    const item = await publishItem(seller, uniqueTitle('待治理商品'));
    await login(buyer, 'buyer');
    await buyer.goto(`/detail.html?id=${item.id}`);
    await buyer.locator('button[data-product-action="report"]').click();
    const reportDialog = buyer.locator('#contentReportDialog');
    await expect(reportDialog).toBeVisible();
    await reportDialog.locator('select[name="reasonCode"]').selectOption('FRAUD');
    await reportDialog.locator('textarea[name="description"]').fill('该商品信息存在明显虚假内容，请管理员核查处理。');
    const reportCreated = buyer.waitForResponse(response => response.url().endsWith('/api/reports')
      && response.request().method() === 'POST', { timeout: 30_000 });
    await reportDialog.locator('form').evaluate(form => form.requestSubmit());
    expect((await reportCreated).ok()).toBeTruthy();
    await expect(reportDialog).toBeHidden({ timeout: 15_000 });

    const publicQuestion = `待管理员删除的留言${Date.now()}`;
    await buyer.locator('#publicQuestion').fill(publicQuestion);
    await buyer.locator('#messageForm button[type="submit"]').click();
    await expect(buyer.locator('#messageList')).toContainText(publicQuestion);

    await buyer.goto('/admin.html');
    await expect(buyer.locator('#adminGate')).toBeVisible();
    await expect(buyer.locator('#adminPanel')).toBeHidden();

    await login(admin, 'admin');
    await admin.goto('/admin.html');
    await expect(admin.locator('#adminPanel')).toBeVisible();
    await admin.locator('[data-admin-tab="reports"]').click();
    const report = admin.locator('.admin-report-card').filter({ hasText: item.title }).first();
    await expect(report).toBeVisible();
    await acceptDialogOnClick(admin, report.locator('[data-action="resolve-report"]'),
      'E2E 核查确认商品违规并执行下架');
    await expect(admin.locator('#adminReportMessage')).toContainText('治理措施正在处理中');
    await expect.poll(async () => {
      const items = await api(admin, '/admin/items');
      return items.find(candidate => candidate.id === item.id)?.moderationStatus;
    }, { timeout: 30_000, intervals: [500, 1_000, 2_000] }).toBe('REMOVED');
    await admin.reload();
    await expect(admin.locator('#adminPanel')).toBeVisible();
    await expect(admin.locator('#adminItemList').filter({ hasText: item.title })).toContainText('已下架');

    await admin.locator('[data-admin-tab="messages"]').click();
    const messageRow = admin.locator('#adminMessageList .admin-row').filter({ hasText: publicQuestion }).first();
    await expect(messageRow).toBeVisible();
    const dialog = admin.waitForEvent('dialog').then(d => d.accept());
    await Promise.all([dialog, messageRow.locator('[data-action="delete-message"]').click()]);
    await admin.waitForTimeout(300);
    await expect(admin.locator('#adminMessageList')).not.toContainText(publicQuestion);

    await buyer.goto('/reports.html');
    await expect(buyer.locator('#myReportList')).toContainText(item.title);
    await expect(buyer.locator('#myReportList')).toContainText('处理说明');

    const forbidden = await buyer.request.get('/api/admin/messages');
    expect(forbidden.status()).toBe(403);

    await admin.locator('[data-admin-tab="users"]').click();
    const buyerRow = admin.locator('#adminUserList .admin-row').filter({ hasText: ACCOUNTS.buyer.email });
    await expect(buyerRow).toContainText('正常');
    await buyerRow.getByRole('button', { name: '禁用' }).click();
    const disabledBuyer = admin.locator('#adminUserList .admin-row').filter({ hasText: ACCOUNTS.buyer.email });
    await expect(disabledBuyer).toContainText('已禁用');
    await disabledBuyer.getByRole('button', { name: '恢复' }).click();
    await expect(admin.locator('#adminUserList .admin-row').filter({ hasText: ACCOUNTS.buyer.email })).toContainText('正常');
  } finally {
    await sellerContext.close();
    await buyerContext.close();
  }
});
