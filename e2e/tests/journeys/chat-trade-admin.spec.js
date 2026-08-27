const { test, expect } = require('../fixtures/evidence');
const { ACCOUNTS, login, publishItem } = require('../fixtures/app');

function uniqueTitle(prefix) {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
}

async function acceptDialogOnClick(page, locator, promptText) {
  const handled = page.waitForEvent('dialog').then(dialog => dialog.accept(promptText));
  await Promise.all([handled, locator.click()]);
}

test('私聊→未读→屏蔽', async ({ page: buyer, browser }) => {
  const sellerContext = await browser.newContext();
  const seller = await sellerContext.newPage();
  const message = `E2E 私聊未读消息 ${Date.now()}`;
  try {
    await login(seller, 'seller');
    const item = await publishItem(seller, uniqueTitle('私聊商品'));
    await login(buyer, 'buyer');
    await buyer.goto(`/detail.html?id=${item.id}`);
    await buyer.locator('button[data-product-action="chat"]').first().click();
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
    await seller.locator('#toggleChatBlock').click();
    await expect(seller.locator('#toggleChatBlock')).toHaveText('解除屏蔽');
    await expect(seller.locator('#chatBody')).toBeDisabled();
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
    await buyer.locator('button[data-product-action="request"]').first().click();
    await expect(buyer.locator('.purchase-request-notice')).toContainText('购买意向已提交');
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
    await sellerOrder.locator('[data-order-action="ACCEPT"]').click();
    await seller.locator('[data-confirm-action]').click();
    await expect(seller.locator(`[data-order-record="${orderId}"]`)).toContainText('待当面交易');

    await buyer.reload();
    const selectedOrder = buyer.locator(`[data-order-record="${orderId}"]`);
    await expect(selectedOrder).toContainText('待当面交易');
    await selectedOrder.locator('[data-order-action="COMPLETE"]').click();
    await buyer.locator('[data-confirm-action]').click();
    await expect(buyer.locator(`[data-order-record="${orderId}"]`)).toContainText('交易完成');
  } finally {
    await sellerContext.close();
  }
});

test('管理员举报治理及用户管理', async ({ page: admin, browser }) => {
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
    await reportDialog.locator('textarea[name="description"]').fill('该商品信息存在明显虚假内容，请管理员核查处理。');
    await reportDialog.locator('button[type="submit"]').click();
    await expect(reportDialog).toBeHidden();

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
    await expect(admin.locator('#adminReportMessage')).toContainText('举报处理完成');
    await expect(admin.locator('#adminItemList').filter({ hasText: item.title })).toContainText('已下架');

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
