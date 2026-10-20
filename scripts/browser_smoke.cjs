/* 使用普通 Playwright API 验证隔离副本，不启动任何 MCP 服务。 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const origin = process.env.ONTOLOGY_SMOKE_ORIGIN || 'http://127.0.0.1:18083';
assert.equal(origin, 'http://127.0.0.1:18083', '只允许本机隔离测试入口');
const output = path.resolve(__dirname, '../output/playwright');
fs.mkdirSync(output, { recursive: true });

(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const checks = [];
  const errors = [];
  let createdProject;
  let adminContext;
  const check = (name, condition) => { assert(condition, name); checks.push(name); };
  try {
    for (const [username, count] of [['admin', 3], ['test_project_viewer', 1], ['test_no_project', 0], ['test_isolation_owner', 1]]) {
      const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
      const page = await context.newPage();
      page.on('pageerror', error => errors.push(error.message));
      await page.goto(origin + '/ontology-management/');
      await page.getByPlaceholder('输入用户名').fill(username);
      await page.getByPlaceholder('输入密码').fill(username);
      await page.getByRole('button', { name: '账号密码登录' }).click();
      await page.getByRole('button', { name: '新建项目' }).waitFor();
      await page.waitForFunction(expected => document.querySelectorAll('tbody tr[data-row-key]').length === expected, count);
      if (count === 0) await page.locator('.ant-empty-description').filter({ hasText: /暂无数据|暂无项目|No [dD]ata/ }).waitFor();
      check('scoped_projects_' + username, await page.locator('tbody tr[data-row-key]').count() === count);
      check('canonical_return_path_' + username, new URL(page.url()).pathname === '/ontology-management/home');
      if (username === 'test_project_viewer') {
        check('viewer_has_no_project_edit_delete', await page.getByRole('button', { name: /编\s*辑/ }).count() === 0
          && await page.getByRole('button', { name: /删\s*除/ }).count() === 0);
        await page.getByRole('button', { name: /详\s*情/ }).click();
        await page.getByRole('button', { name: '打开用户菜单' }).waitFor();
        await page.waitForURL('**/ontology');
        check('viewer_can_open_graph_page', (await page.locator('body').innerText()).includes('本体图谱'));
      }
      if (username === 'admin') {
        adminContext = context;
        await page.screenshot({ path: path.join(output, 'admin-projects.png'), fullPage: true });

        await page.getByRole('button', { name: '新建项目' }).click();
        await page.getByPlaceholder('请输入项目名称').fill('Ontology浏览器验收临时项目');
        await page.getByPlaceholder('请输入项目描述').fill('只在隔离副本创建并在测试后删除');
        const [saved] = await Promise.all([
          page.waitForResponse(r => new URL(r.url()).pathname === '/api/v1/organization/save'),
          page.getByRole('button', { name: /^确\s*定$/ }).click(),
        ]);
        check('ui_create_with_csrf', saved.status() === 200);
        createdProject = String((await saved.json()).data.id);
        await page.waitForURL('**/manage/' + createdProject + '/ontology');
        check('new_project_route_renders', (await page.locator('body').innerText()).includes('本体图谱'));
      }
      await page.getByRole('button', { name: '打开用户菜单' }).click();
      await page.getByRole('menuitem', { name: '退出登录', exact: true }).click();
      await page.getByRole('button', { name: /^确\s*认$/ }).click();
      await page.getByPlaceholder('输入用户名').waitFor();
      check('logout_' + username, (await context.request.get(origin + '/api/v1/auth/me')).status() === 401);
      if (username !== 'admin') await context.close();
    }
    const mobile = await browser.newPage({ viewport: { width: 390, height: 844 } });
    await mobile.goto(origin + '/ontology-management/login');
    await mobile.getByPlaceholder('输入用户名').waitFor();
    await mobile.waitForFunction(() => Array.from(document.querySelectorAll('button')).some(button => button.textContent.includes('账号密码登录') && !button.disabled));
    check('mobile_password_login_available', await mobile.getByRole('button', { name: '账号密码登录' }).isEnabled());
    check('mobile_login_has_no_horizontal_overflow', await mobile.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    await mobile.screenshot({ path: path.join(output, 'mobile-login.png'), fullPage: true });
    check('no_browser_runtime_errors', errors.length === 0);
  } finally {
    // 清理只作用于本次创建的项目，绝不删除测试种子或旧数据。
    if (createdProject && adminContext) {
      const api = adminContext.request;
      const csrf = await (await api.get(origin + '/api/v1/auth/csrf')).json();
      await api.post(origin + '/api/v1/auth/login', { form: { username: 'admin', password: 'admin' }, headers: { Origin: origin, [csrf.headerName]: csrf.token } });
      const current = await (await api.get(origin + '/api/v1/auth/csrf')).json();
      const result = await api.post(origin + '/api/v1/organization/delete?organizationId=' + createdProject, { headers: { Origin: origin, [current.headerName]: current.token } });
      assert.equal(result.status(), 200, '必须清理本次临时项目');
    }
    await browser.close();
  }
  fs.writeFileSync(path.join(output, 'report.json'), JSON.stringify({ checks, passed: true }, null, 2));
  console.log('浏览器验收通过：' + checks.length + ' 项；截图和报告保存在 output/playwright/。');
})().catch(error => { console.error(error.message); process.exit(1); });
