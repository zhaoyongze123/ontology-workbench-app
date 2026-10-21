const http = require('http');
const fs = require('fs');
const path = require('path');
const playwrightModule = process.env.PLAYWRIGHT_MODULE || 'playwright';
const { chromium } = require(playwrightModule);

const root = path.resolve(process.argv[2] || path.join(__dirname, '..', 'apps', 'web', 'dist-multiuser'));
// 该脚本使用本地静态构建与模拟 API，只核对前端请求契约，不替代真实后端集成测试。
const mime = { '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
const server = http.createServer((req, res) => {
  const pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname).replace(/^\/ontology-management\/?/, '');
  const candidate = path.resolve(root, pathname || 'index.html');
  const file = candidate.startsWith(root) && fs.existsSync(candidate) && fs.statSync(candidate).isFile()
    ? candidate : path.join(root, 'index.html');
  res.setHeader('Content-Type', mime[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});

let browser;
(async () => {
  try {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    browser = await chromium.launch({ headless: true });
    const page = await browser.newPage();
    page.setDefaultTimeout(5000);
    const tokenPosts = [];
    let loginUnauthorized = false;
    let accountActionInvalid = false;
    let adminUsersForbidden = false;
    let adminClientsRateLimited = false;
    let currentUserFailureCount = 0;
    let failCurrentUserOnce = false;

    await page.route('**/api/v1/**', async route => {
      const req = route.request();
      const pathname = new URL(req.url()).pathname;
      let body = {};
      try { body = req.postDataJSON() || {}; } catch {}
      if (req.method() === 'POST' && (pathname.endsWith('/auth/account-actions/inspect') || pathname.endsWith('/auth/activate'))) {
        tokenPosts.push({ pathname, body });
      }
      if (pathname.endsWith('/auth/login') && loginUnauthorized) {
        await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ error: 'invalid_credentials' }) });
        return;
      }
      if (pathname.endsWith('/auth/activate') && accountActionInvalid) {
        await route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ error: 'token_invalid' }) });
        return;
      }
      if (pathname.endsWith('/admin/users') && adminUsersForbidden) {
        await route.fulfill({ status: 403, contentType: 'application/json', body: JSON.stringify({ error: 'forbidden' }) });
        return;
      }
      if (pathname.endsWith('/admin/oauth/clients') && adminClientsRateLimited) {
        await route.fulfill({ status: 429, contentType: 'application/json', body: JSON.stringify({ error: 'too_many_attempts' }) });
        return;
      }
      if (pathname.endsWith('/auth/me')) {
        currentUserFailureCount += 1;
        if (failCurrentUserOnce && currentUserFailureCount === 1) {
          await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ error: 'service_unavailable' }) });
          return;
        }
      }
      let value = {};
      if (pathname.endsWith('/auth/options')) value = { passwordEnabled: true, workbenchEnabled: true };
      else if (pathname.endsWith('/auth/csrf')) value = { token: 'csrf-mock', headerName: 'X-CSRF-TOKEN' };
      else if (pathname.endsWith('/auth/login')) value = { status: 'ok' };
      else if (pathname.endsWith('/auth/account-actions/inspect')) value = { purpose: 'ACTIVATE', valid: true };
      else if (pathname.endsWith('/auth/activate')) value = { status: 'activated' };
      else if (pathname.endsWith('/auth/me')) value = { id: 'admin', username: 'admin', displayName: '管理员', systemAdmin: true, enabled: true, authVersion: 0 };
      else if (pathname.endsWith('/admin/users')) value = { items: [{ id: 'u1', username: 'test_user', displayName: '测试用户', systemAdmin: false, enabled: true, passwordActivated: false, createTime: '2026-10-20T10:00:00' }], page: 0, size: 20, total: 1 };
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(value) });
    });

    const base = `http://127.0.0.1:${server.address().port}/ontology-management`;
    await page.goto(`${base}/account/activate#token=smoke-token`, { waitUntil: 'domcontentloaded' });
    await page.locator('#password').fill('VeryLongPassword123');
    await page.locator('#confirmPassword').fill('VeryLongPassword123');
    await page.getByRole('button', { name: '确认设置' }).click();
    await page.getByText('密码已设置').waitFor();
    if (new URL(page.url()).hash) throw new Error('activation fragment was not removed');
    if (tokenPosts.length !== 2 || tokenPosts.some(item => item.body.token !== 'smoke-token')) throw new Error('account token did not use only the inspect/submit POST contracts');
    console.log('PASS: account action page clears fragment and sends token only in inspect/submit POST bodies');

    await page.goto(`${base}/login?oauth_return=%2Fapi%2Fv1%2Foidc%2Fapp%2Fauthorize%3Fclient_id%3Ddemo`, { waitUntil: 'domcontentloaded' });
    await page.locator('input[autocomplete="username"]').fill('alice');
    await page.locator('input[autocomplete="current-password"]').fill('password');
    await page.getByRole('button', { name: '账号密码登录' }).click();
    await page.waitForURL('**/api/v1/oidc/app/authorize?client_id=demo');
    console.log('PASS: login hands off to the validated OAuth authorize return');

    await page.goto(`${base}/admin/users`, { waitUntil: 'domcontentloaded' });
    await page.getByText('test_user').waitFor();
    console.log('PASS: admin users route renders against mocked raw API contract');

    // #15 的失败态核对：页面私有错误可见，通用协议 code 不直接展示。
    loginUnauthorized = true;
    await page.goto(`${base}/login`, { waitUntil: 'domcontentloaded' });
    await page.locator('input[autocomplete="username"]').fill('alice');
    await page.locator('input[autocomplete="current-password"]').fill('wrong-password');
    await page.getByRole('button', { name: '账号密码登录' }).click();
    await page.getByText('用户名或密码错误').waitFor();
    if (await page.locator('.ant-message-notice').count()) throw new Error('skipErrorHandler leaked a global login toast');
    loginUnauthorized = false;
    console.log('PASS: login skipErrorHandler keeps 401 feedback in the login form');

    accountActionInvalid = true;
    await page.goto(`${base}/account/activate#token=expired-smoke-token`, { waitUntil: 'domcontentloaded' });
    await page.locator('#password').fill('VeryLongPassword123');
    await page.locator('#confirmPassword').fill('VeryLongPassword123');
    await page.getByRole('button', { name: '确认设置' }).click();
    await page.getByText('链接已失效或已使用，请联系系统管理员重新生成。').waitFor();
    if (await page.locator('.ant-message-notice').count()) throw new Error('skipErrorHandler leaked a global account-action toast');
    accountActionInvalid = false;
    console.log('PASS: account-action skipErrorHandler preserves its own invalid-link state');

    adminUsersForbidden = true;
    await page.goto(`${base}/admin/users`, { waitUntil: 'domcontentloaded' });
    await page.getByText('当前账号没有执行此操作的权限。').first().waitFor();
    if (await page.getByText('forbidden', { exact: true }).count()) throw new Error('403 internal code was shown to the user');
    adminUsersForbidden = false;
    console.log('PASS: 403 forbidden maps to the explicit Chinese permission-change message');

    adminClientsRateLimited = true;
    await page.goto(`${base}/admin/oauth-clients`, { waitUntil: 'domcontentloaded' });
    await page.getByText('尝试次数过多，请稍后重试。').first().waitFor();
    if (await page.getByText('too_many_attempts', { exact: true }).count()) throw new Error('429 internal code was shown to the user');
    adminClientsRateLimited = false;
    console.log('PASS: 429 rate-limit code maps to Chinese retry guidance');

    currentUserFailureCount = 0;
    failCurrentUserOnce = true;
    await page.goto(`${base}/admin/users`, { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: '重新验证' }).waitFor();
    await page.getByRole('button', { name: '重新验证' }).click();
    await page.getByText('test_user').waitFor();
    if (currentUserFailureCount < 2) throw new Error('session retry did not re-request current user');
    failCurrentUserOnce = false;
    console.log('PASS: transient current-session verification offers a manual retry');
  } finally {
    if (browser) await browser.close();
    server.close();
  }
})().catch(error => { console.error(error); process.exit(1); });
