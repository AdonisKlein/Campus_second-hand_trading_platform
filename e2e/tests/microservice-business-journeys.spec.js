const { test, expect } = require('./fixtures/evidence');
const { api, login, publishItem } = require('./fixtures/app');

async function actorPage(browser, role) {
  const context = await browser.newContext();
  const page = await context.newPage();
  await login(page, role);
  return { context, page };
}

async function poll(operation, predicate, message, timeoutMs = 30_000) {
  const deadline = Date.now() + timeoutMs;
  let value;
  while (Date.now() < deadline) {
    value = await operation();
    if (predicate(value)) return value;
    await new Promise(resolve => setTimeout(resolve, 300));
  }
  expect(predicate(value), message).toBeTruthy();
  return value;
}

test('UC04 私聊消息仅买卖双方可见且未读数可更新', async ({ browser }) => {
  const seller = await actorPage(browser, 'seller');
  const buyer = await actorPage(browser, 'buyer');
  try {
    const item = await publishItem(seller.page, `E2E私聊商品${Date.now()}`);
    const conversation = await api(buyer.page, '/chat/conversations', {
      method: 'POST', body: { itemId: item.id }
    });
    const sent = await api(buyer.page, `/chat/conversations/${conversation.id}/messages`, {
      method: 'POST', body: { body: '可以在校内公共地点当面验货吗？' }
    });
    const inbox = await api(seller.page, '/chat/conversations');
    expect(inbox.totalUnread).toBeGreaterThan(0);
    const history = await api(seller.page, `/chat/conversations/${conversation.id}/messages`);
    expect(history.messages.map(message => message.body)).toContain('可以在校内公共地点当面验货吗？');
    await api(seller.page, `/chat/conversations/${conversation.id}/read`, {
      method: 'POST', body: { throughSequence: sent.sequence }
    });
    expect((await api(seller.page, '/chat/unread-count')).count).toBe(0);

    const stranger = await actorPage(browser, 'admin');
    try {
      const response = await stranger.page.request.get(`/api/chat/conversations/${conversation.id}/messages`);
      expect(response.status()).toBe(403);
    } finally { await stranger.context.close(); }
  } finally {
    await buyer.context.close();
    await seller.context.close();
  }
});

test('UC02 商品与用户搜索支持筛选排序且不泄露私密资料', async ({ browser }) => {
  const seller = await actorPage(browser, 'seller');
  const visitor = await browser.newPage();
  try {
    const title = `E2E筛选商品${Date.now()}`;
    const item = await publishItem(seller.page, title);
    const itemSearch = await api(visitor,
      `/search?scope=ITEMS&q=${encodeURIComponent(title)}&minPrice=20&maxPrice=30`
      + `&region=${encodeURIComponent('学院路校区')}&tags=${encodeURIComponent('支持验货')}&sort=PRICE_ASC`);
    expect(itemSearch.items.map(candidate => candidate.id)).toContain(item.id);
    expect(itemSearch.items.every(candidate => candidate.price >= 20 && candidate.price <= 30)).toBeTruthy();
    expect(itemSearch.items.every(candidate => candidate.region === '学院路校区')).toBeTruthy();

    const userSearch = await api(visitor,
      '/search?scope=USERS&q=e2e&sort=CREDIT');
    const sellerHit = userSearch.users.find(user => user.id === 3);
    expect(sellerHit).toMatchObject({ username: 'e2e_seller', nickname: 'E2E 卖家', region: '沙河校区' });
    expect(sellerHit).not.toHaveProperty('email');
    expect(sellerHit).not.toHaveProperty('phone');
    expect(sellerHit).not.toHaveProperty('passwordHash');

    const regionalUsers = await api(visitor,
      `/search?scope=USERS&region=${encodeURIComponent(sellerHit.region)}&sort=CREDIT`);
    expect(regionalUsers.users.map(user => user.id)).toContain(3);

    const allStudents = await api(visitor, '/search?scope=USERS&sort=CREDIT');
    expect(allStudents.users.map(user => user.username)).not.toContain('e2e_admin');
  } finally {
    await visitor.context().close();
    await seller.context.close();
  }
});

test('UC05-UC06 购买意向经卖家选定后完成并进入本人交易记录', async ({ browser }) => {
  const seller = await actorPage(browser, 'seller');
  const buyer = await actorPage(browser, 'buyer');
  try {
    const item = await publishItem(seller.page, `E2E交易商品${Date.now()}`);
    const order = await api(buyer.page, '/orders', { method: 'POST', body: { itemId: item.id } });
    expect(order.status).toBe('PURCHASE_REQUESTED');

    const accepted = await api(seller.page, `/orders/${order.id}/actions`, {
      method: 'POST', body: { action: 'ACCEPT' }
    });
    expect(accepted.status).toBe('PURCHASE_REQUESTED');
    await poll(
      () => api(seller.page, '/orders'),
      orders => orders.some(candidate => candidate.id === order.id && candidate.status === 'WAITING_HANDOVER'),
      '商品预留事件应最终使订单进入 WAITING_HANDOVER'
    );

    await api(buyer.page, `/orders/${order.id}/actions`, {
      method: 'POST', body: { action: 'COMPLETE' }
    });
    const buyingDesk = await poll(
      () => api(buyer.page, '/orders/desk?perspective=BUYING&stage=CLOSED'),
      desk => desk.groups.some(group => group.entries.some(entry => entry.id === order.id && entry.status === 'COMPLETED')),
      '买家工作台应展示已完成订单'
    );
    expect(buyingDesk.summary.closed).toBeGreaterThan(0);
    const sellingDesk = await api(seller.page, '/orders/desk?perspective=SELLING&stage=CLOSED');
    expect(sellingDesk.groups.some(group => group.entries.some(entry => entry.id === order.id))).toBeTruthy();
  } finally {
    await buyer.context.close();
    await seller.context.close();
  }
});

test('UC07-UC08 学生跟踪举报且管理员治理结果可审计', async ({ browser }) => {
  test.slow();
  const seller = await actorPage(browser, 'seller');
  const buyer = await actorPage(browser, 'buyer');
  const admin = await actorPage(browser, 'admin');
  try {
    const item = await publishItem(seller.page, `E2E举报商品${Date.now()}`);
    const report = await api(buyer.page, '/reports', {
      method: 'POST',
      body: { targetType: 'ITEM', targetId: item.id, reasonCode: 'SPAM', description: '该商品信息疑似重复发布，请管理员核查处理。' }
    });
    expect(report.status).toBe('OPEN');
    const mine = await api(buyer.page, '/reports/mine');
    expect(mine.reports.some(candidate => candidate.id === report.id)).toBeTruthy();

    const queue = await api(admin.page, '/admin/reports?status=OPEN');
    expect(queue.reports.some(candidate => candidate.id === report.id)).toBeTruthy();
    await api(admin.page, `/admin/reports/${report.id}`, {
      method: 'PUT',
      body: { status: 'RESOLVED', action: 'REMOVE_ITEM', note: '已核实违规重复发布，决定移除商品。' }
    });
    const resolved = await poll(
      () => api(buyer.page, '/reports/mine'),
      page => page.reports.some(candidate => candidate.id === report.id
        && candidate.status === 'RESOLVED' && candidate.actionState === 'APPLIED'
        && candidate.history.length > 0),
      '治理事件应最终应用并保留审计历史', 45_000
    );
    expect(resolved.reports.find(candidate => candidate.id === report.id).resolutionNote).toContain('已核实');
    const detailResponse = await buyer.page.request.get(`/api/items/${item.id}`);
    const detail = await detailResponse.json();
    expect(detail.success).toBe(false);
  } finally {
    await admin.context.close();
    await buyer.context.close();
    await seller.context.close();
  }
});

test('UC07-UC08 留言和用户举报会执行匹配治理措施并留下审计记录', async ({ browser }) => {
  test.slow();
  const seller = await actorPage(browser, 'seller');
  const buyer = await actorPage(browser, 'buyer');
  const admin = await actorPage(browser, 'admin');
  let sellerRestored = false;
  try {
    const item = await publishItem(seller.page, `E2E多类型举报商品${Date.now()}`);
    const message = await api(buyer.page, '/messages', {
      method: 'POST', body: { itemId: item.id, content: '这是一条用于验证留言举报治理流程的公开留言。' }
    });
    const messageReport = await api(seller.page, '/reports', {
      method: 'POST',
      body: { targetType: 'MESSAGE', targetId: message.id, reasonCode: 'SPAM', description: '该留言包含重复骚扰信息，请管理员核查并移除。' }
    });
    const userReport = await api(buyer.page, '/reports', {
      method: 'POST',
      body: { targetType: 'USER', targetId: 3, reasonCode: 'HARASSMENT', description: '该用户存在持续骚扰行为，请管理员核查账号状态。' }
    });

    await api(admin.page, `/admin/reports/${messageReport.id}`, {
      method: 'PUT', body: { status: 'RESOLVED', action: 'REMOVE_MESSAGE', note: '核实留言违规并执行移除。' }
    });

    const sellerReports = await poll(
      () => api(seller.page, '/reports/mine'),
      page => page.reports.some(report => report.id === messageReport.id
        && report.actionState === 'APPLIED' && report.history.length > 0),
      '留言移除治理事件应生效并保留审计历史', 45_000
    );
    expect(sellerReports.reports.find(report => report.id === messageReport.id).decisionAction)
      .toBe('REMOVE_MESSAGE');
    const questions = await api(buyer.page, `/messages/item/${item.id}`);
    expect(questions.map(candidate => candidate.id)).not.toContain(message.id);

    await api(admin.page, `/admin/reports/${userReport.id}`, {
      method: 'PUT', body: { status: 'RESOLVED', action: 'DISABLE_USER', note: '核实账号违规并执行禁用。' }
    });

    const buyerReports = await poll(
      () => api(buyer.page, '/reports/mine'),
      page => page.reports.some(report => report.id === userReport.id
        && report.actionState === 'APPLIED' && report.history.length > 0),
      '用户禁用治理事件应生效并保留审计历史', 45_000
    );
    expect(buyerReports.reports.find(report => report.id === userReport.id).decisionAction)
      .toBe('DISABLE_USER');
    await expect.poll(async () => (await seller.page.request.get('/api/users/me')).status(), {
      timeout: 15_000, intervals: [300, 500, 1_000]
    }).toBe(401);
    await api(admin.page, '/admin/users/3/status', { method: 'PUT', body: { status: 'ACTIVE' } });
    await poll(async () => {
      const response = await buyer.page.request.get(`/api/items/${item.id}`);
      return response.status();
    }, status => status === 200, '恢复用户后公开资料投影应重新激活其商品');
    sellerRestored = true;
  } finally {
    if (!sellerRestored) {
      await api(admin.page, '/admin/users/3/status', { method: 'PUT', body: { status: 'ACTIVE' } }).catch(() => {});
    }
    await admin.context.close();
    await buyer.context.close();
    await seller.context.close();
  }
});
