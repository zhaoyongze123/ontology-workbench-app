const assert = require("assert/strict");
const http = require("http");
const fs = require("fs");
const path = require("path");

const playwrightModule = process.env.PLAYWRIGHT_MODULE || "playwright";
const { chromium } = require(playwrightModule);
const root = path.resolve(
  process.argv[2] || path.join(__dirname, "..", "apps", "web", "dist-multiuser")
);
const mime = {
  ".html": "text/html",
  ".js": "application/javascript",
  ".css": "text/css",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".ico": "image/x-icon",
  ".woff2": "font/woff2",
};

const users = [
  {
    id: "u1",
    username: "target_user",
    displayName: "目标用户",
    systemAdmin: false,
    enabled: true,
    passwordActivated: true,
    createTime: "2026-10-20T10:00:00",
  },
  {
    id: "u2",
    username: "pending_user",
    displayName: "待设置用户",
    systemAdmin: false,
    enabled: false,
    passwordActivated: false,
    createTime: "2026-10-19T08:00:00",
  },
];
const clients = [
  {
    id: "c1",
    clientId: "ontology_client_id_with_a_long_suffix_123456789",
    displayName: "Workbench Ontology",
    redirectUri:
      "https://workbench.example.com/ontology/oauth/callback?tenant=long-value",
    scopes: ["profile", "ontology.read", "ontology.manage"],
    enabled: true,
    createdAt: "2026-10-21T02:00:00Z",
  },
  {
    id: "c2",
    clientId: "ontology_disabled_client",
    displayName: "停用的客户端",
    redirectUri: "https://disabled.example.com/oauth/callback",
    scopes: ["profile"],
    enabled: false,
    createdAt: "2026-10-20T02:00:00Z",
  },
  {
    id: "c-existing-match",
    clientId: "ontology_existing_same_name",
    displayName: "新 Workbench 客户端",
    redirectUri: "https://app.example.com/oidc/callback",
    scopes: ["profile"],
    enabled: true,
    createdAt: "2026-10-19T02:00:00Z",
  },
];
const auditEvents = Array.from({ length: 51 }, (_, index) => ({
  id: 100 - index,
  eventType:
    index === 0
      ? "SECURITY_AUTHENTICATION_FAILURE"
      : index === 1
      ? "ACCOUNT_DISABLED"
      : "PROJECT_CREATED",
  actorUserId: index === 0 ? null : "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  details:
    index === 0
      ? {
          requestId: "req-smoke-123",
          path: "/api/v1/auth/login",
          method: "POST",
          status: 401,
          peer: "127.0.0.1",
        }
      : index === 1
      ? { targetUserId: "target-user-123" }
      : { projectId: String(9000 + index), capability: "CREATE_PROJECT" },
  createdAt: new Date(Date.now() - 60 * 1000).toISOString(),
}));

const calls = {
  userDisable: [],
  clientDisable: [],
  audit: [],
  createdUser: 0,
  createdClient: 0,
};
let failDisableOnce = true;
let failUserCreateOnce = true;
let failClientDisableOnce = true;
let failClientCreateOnce = true;
let incompleteNextClientList = false;
let currentUserIsAdmin = true;
let server;
let browser;

function response(route, status, body, contentType = "application/json") {
  return route.fulfill({ status, contentType, body: JSON.stringify(body) });
}

function userPage() {
  return { items: users.slice(), page: 0, size: 20, total: users.length };
}

async function assertModalFitsViewport(page, label) {
  const box = await page
    .locator(".ant-modal")
    .last()
    .evaluate((element) => {
      const rect = element.getBoundingClientRect();
      return {
        left: rect.left,
        right: rect.right,
        viewport: window.innerWidth,
      };
    });
  assert.ok(
    box.left >= 0 && box.right <= box.viewport,
    `${label} must fit the viewport: ${JSON.stringify(box)}`
  );
}

server = http.createServer((req, res) => {
  const pathname = decodeURIComponent(
    new URL(req.url, "http://localhost").pathname
  ).replace(/^\/ontology-management\/?/, "");
  const candidate = path.resolve(root, pathname || "index.html");
  const file =
    candidate.startsWith(root) &&
    fs.existsSync(candidate) &&
    fs.statSync(candidate).isFile()
      ? candidate
      : path.join(root, "index.html");
  res.setHeader(
    "Content-Type",
    mime[path.extname(file)] || "application/octet-stream"
  );
  fs.createReadStream(file).pipe(res);
});

(async () => {
  try {
    await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
    browser = await chromium.launch({
      headless: true,
      executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH || undefined,
    });
    const context = await browser.newContext({
      viewport: { width: 1440, height: 900 },
    });
    await context.addInitScript(() => {
      // 模拟浏览器拒绝剪贴板授权，验证仍可人工保存。
      Object.defineProperty(navigator, "clipboard", {
        configurable: true,
        value: {
          writeText: async () => {
            throw new Error("clipboard permission denied");
          },
        },
      });
    });
    const page = await context.newPage();
    page.setDefaultTimeout(8000);
    await page.route("**/api/v1/**", async (route) => {
      const request = route.request();
      const url = new URL(request.url());
      const endpoint = url.pathname;
      let body = {};
      try {
        body = request.postDataJSON() || {};
      } catch {
        /* GET 请求没有 JSON 正文。 */
      }

      if (endpoint.endsWith("/auth/csrf"))
        return response(route, 200, {
          token: "csrf-smoke",
          headerName: "X-CSRF-TOKEN",
        });
      if (endpoint.endsWith("/auth/me"))
        return response(route, 200, {
          id: "admin-1",
          username: "admin",
          displayName: "系统管理员",
          systemAdmin: currentUserIsAdmin,
          enabled: true,
          authVersion: 1,
        });
      if (endpoint.endsWith("/admin/users") && request.method() === "GET")
        return response(route, 200, userPage());
      if (endpoint.endsWith("/admin/users") && request.method() === "POST") {
        calls.createdUser += 1;
        if (failUserCreateOnce) {
          failUserCreateOnce = false;
          return route.abort("timedout");
        }
        return response(route, 201, {
          user: {
            ...users[0],
            id: "u-created",
            username: body.username,
            displayName: body.displayName,
          },
          activationUrl:
            "https://ontology.example/account/activate#token=one-time-smoke-token",
        });
      }
      if (
        endpoint.endsWith("/admin/users/u1/enabled") &&
        request.method() === "PATCH"
      ) {
        calls.userDisable.push(body.enabled);
        if (failDisableOnce) {
          failDisableOnce = false;
          return response(
            route,
            409,
            {
              type: "urn:ontology:problem:account-last-admin",
              title: "状态冲突",
              status: 409,
              detail: "至少需要保留一个启用的系统管理员。",
              errorCode: "ACCOUNT_LAST_ADMIN",
              requestId: "req-disable-smoke",
            },
            "application/problem+json"
          );
        }
        const user = { ...users[0], enabled: body.enabled };
        users[0] = user;
        return response(route, 200, user);
      }
      if (endpoint.endsWith("/admin/users/u1/password-reset-link"))
        return response(route, 200, {
          resetUrl:
            "https://ontology.example/account/reset-password#token=reset-smoke",
        });
      if (endpoint.endsWith("/admin/users/u2/activation-link"))
        return response(route, 200, {
          activationUrl:
            "https://ontology.example/account/activate#token=activation-smoke",
        });
      if (
        endpoint.endsWith("/admin/oauth/clients") &&
        request.method() === "GET"
      ) {
        if (incompleteNextClientList) {
          incompleteNextClientList = false;
          return response(route, 200, { items: clients.slice() });
        }
        return response(route, 200, clients.slice());
      }
      if (
        endpoint.endsWith("/admin/oauth/clients") &&
        request.method() === "POST"
      ) {
        calls.createdClient += 1;
        if (failClientCreateOnce) {
          failClientCreateOnce = false;
          clients.unshift({
            id: "c-uncertain",
            clientId: "ontology_uncertain_123",
            displayName: body.displayName,
            redirectUri: body.redirectUri,
            scopes: body.scopes,
            enabled: true,
            createdAt: "2026-10-21T02:30:00Z",
          });
          return route.abort("timedout");
        }
        const created = {
          clientId: "ontology_created_123",
          clientSecret: "example-client-secret-once",
          displayName: body.displayName,
          redirectUri: body.redirectUri,
          scopes: body.scopes,
        };
        clients.unshift({
          id: "c-created-success",
          ...created,
          enabled: true,
          createdAt: "2026-10-21T02:30:00Z",
        });
        return response(route, 201, created);
      }
      if (
        endpoint.endsWith("/admin/oauth/clients/c1") &&
        request.method() === "DELETE"
      ) {
        calls.clientDisable.push(endpoint);
        if (failClientDisableOnce) {
          failClientDisableOnce = false;
          return route.abort("timedout");
        }
        clients[0].enabled = false;
        return response(route, 200, { status: "disabled" });
      }
      if (
        endpoint.endsWith("/admin/audit-events") &&
        request.method() === "GET"
      ) {
        const beforeId = Number(url.searchParams.get("beforeId"));
        const eventTypes = (url.searchParams.get("eventTypes") || "")
          .split(",")
          .filter(Boolean);
        const actorUserId = url.searchParams.get("actorUserId") || "";
        const startTime = url.searchParams.get("startTime") || "";
        const endTime = url.searchParams.get("endTime") || "";
        calls.audit.push({
          limit: Number(url.searchParams.get("limit")),
          beforeId: Number.isFinite(beforeId) ? beforeId : undefined,
          eventTypes,
          actorUserId,
          startTime,
          endTime,
        });
        let filtered = auditEvents.slice();
        if (beforeId) filtered = filtered.filter((event) => event.id < beforeId);
        if (eventTypes.length)
          filtered = filtered.filter((event) => eventTypes.includes(event.eventType));
        if (actorUserId)
          filtered = filtered.filter((event) => event.actorUserId === actorUserId);
        if (startTime) filtered = filtered.filter((event) => Date.parse(event.createdAt) >= Date.parse(startTime));
        if (endTime) filtered = filtered.filter((event) => Date.parse(event.createdAt) <= Date.parse(endTime));
        return response(route, 200, filtered.slice(0, Number(url.searchParams.get("limit")) || 20));
      }
      return response(route, 200, {});
    });

    const base = `http://127.0.0.1:${
      server.address().port
    }/ontology-management`;
    await page.goto(`${base}/admin/users`, { waitUntil: "networkidle" });
    await page.getByRole("heading", { name: "用户管理" }).waitFor();
    await page.getByText("目标用户").waitFor();
    for (const heading of ["账号状态", "本地密码", "系统角色"]) {
      await page
        .getByRole("columnheader", { name: heading, exact: true })
        .waitFor();
    }
    await page.setViewportSize({ width: 1280, height: 720 });
    const standardWidths = await page.evaluate(() => ({
      viewport: window.innerWidth,
      document: document.documentElement.scrollWidth,
    }));
    assert.ok(
      standardWidths.document <= standardWidths.viewport,
      `1280px layout must not scroll horizontally: ${JSON.stringify(
        standardWidths
      )}`
    );
    await page.setViewportSize({ width: 1440, height: 900 });
    console.log(
      "PASS: user state, password state, and system role are distinct columns"
    );

    const targetRow = page.getByRole("row").filter({ hasText: "target_user" });
    await targetRow.getByRole("button", { name: "停用用户" }).click();
    const disableDialog = page
      .getByRole("dialog")
      .filter({ hasText: "确定停用" });
    await disableDialog.getByRole("button", { name: /取\s*消/ }).click();
    assert.equal(calls.userDisable.length, 0, "cancel must not send PATCH");
    await targetRow.getByRole("button", { name: "停用用户" }).click();
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "停用用户" })
      .click();
    await page
      .getByRole("alert")
      .filter({ hasText: "至少需要保留一个启用的系统管理员。" })
      .waitFor();
    assert.equal(
      calls.userDisable.length,
      1,
      "first confirmation sends only one PATCH"
    );
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "停用用户" })
      .click();
    await targetRow.getByText("已停用").waitFor();
    assert.deepEqual(
      calls.userDisable,
      [false, false],
      "retry must be a user action and update only after server success"
    );
    console.log(
      "PASS: disable cancel is inert, conflict preserves state, retry applies the server result once"
    );

    await page.getByRole("button", { name: "创建用户" }).click();
    const createDialog = page
      .getByRole("dialog")
      .filter({ hasText: "创建用户" })
      .first();
    await createDialog.getByLabel("用户名").fill("new_user");
    await createDialog.getByLabel("显示名称").fill("新用户");
    await createDialog
      .getByRole("button", { name: "创建并生成激活链接" })
      .click();
    await createDialog
      .getByRole("alert")
      .filter({ hasText: "核对账号是否已创建" })
      .waitFor();
    assert.equal(
      calls.createdUser,
      1,
      "network failure does not automatically resend create"
    );
    assert.equal(
      await createDialog
        .getByRole("button", { name: "创建并生成激活链接" })
        .isDisabled(),
      true,
      "unknown create result blocks accidental resubmission"
    );
    await createDialog.getByRole("button", { name: "刷新列表核对" }).click();
    await createDialog
      .getByRole("button", { name: "我已核对，仍要重试" })
      .click();
    const retryDialog = page
      .getByRole("dialog")
      .filter({ hasText: "创建结果仍未确认" });
    await retryDialog.getByRole("button", { name: "我已核对，继续" }).click();
    await createDialog
      .getByRole("button", { name: "创建并生成激活链接" })
      .click();
    const credentialDialog = page
      .getByRole("dialog")
      .filter({ hasText: "激活链接仅展示一次" });
    await credentialDialog.waitFor();
    await page.keyboard.press("Escape");
    await credentialDialog.waitFor();
    await credentialDialog
      .getByRole("button", { name: "复制账号激活链接" })
      .click();
    await credentialDialog
      .getByRole("alert")
      .filter({ hasText: "请在下方手动选择并保存" })
      .waitFor();
    const activationValue = credentialDialog.getByLabel("账号激活链接");
    assert.match(await activationValue.inputValue(), /one-time-smoke-token/);
    assert.equal(
      new URL(page.url()).hash.includes("one-time-smoke-token"),
      false,
      "one-time token must not enter the page URL"
    );
    assert.equal(
      await page.evaluate(() => {
        const values = [localStorage, sessionStorage].flatMap((storage) =>
          Object.keys(storage).map((key) => storage.getItem(key) || "")
        );
        return values.some((value) => value.includes("one-time-smoke-token"));
      }),
      false,
      "one-time token must not enter browser storage"
    );
    await page.keyboard.press("Tab");
    assert.equal(
      await page.evaluate(
        () => document.activeElement?.closest('[role="dialog"]') !== null
      ),
      true,
      "tab focus stays inside the credential dialog"
    );
    await credentialDialog
      .getByRole("button", { name: "已保存，关闭" })
      .click();
    await credentialDialog.waitFor({ state: "detached" });
    assert.equal(
      calls.createdUser,
      2,
      "second create happens only after explicit review and a manual click"
    );
    console.log(
      "PASS: uncertain create is not replayed, needs review, and one-time link survives clipboard failure/Escape"
    );

    await page.getByRole("menuitem", { name: "OAuth 客户端" }).click();
    await page.getByText("Workbench Ontology").waitFor();
    await page.getByText("基本身份", { exact: true }).first().waitFor();
    const disabledClientRow = page
      .getByRole("row")
      .filter({ hasText: "停用的客户端" });
    await disabledClientRow.getByRole("button", { name: "查看详情" }).click();
    const detailDrawer = page
      .getByRole("dialog")
      .filter({ hasText: "OAuth 客户端详情" });
    await detailDrawer.waitFor();
    await detailDrawer.getByText("客户端已停用", { exact: false }).waitFor();
    assert.match(
      await detailDrawer.getByLabel("完整回调地址").inputValue(),
      /disabled\.example\.com/
    );
    await page.keyboard.press("Escape");
    await detailDrawer.waitFor({ state: "hidden" });
    const activeClientRow = page
      .getByRole("row")
      .filter({ hasText: "Workbench Ontology" });
    await activeClientRow.getByRole("button", { name: "查看详情" }).click();
    const activeDetail = page
      .getByRole("dialog")
      .filter({ hasText: "OAuth 客户端详情" });
    await activeDetail.getByRole("button", { name: "复制回调地址" }).click();
    await activeDetail
      .getByRole("alert")
      .filter({ hasText: "手动选择并复制完整值" })
      .waitFor();
    assert.match(
      await activeDetail.getByLabel("完整 Client ID").inputValue(),
      /long_suffix/
    );
    await page.keyboard.press("Escape");
    await activeDetail.waitFor({ state: "hidden" });
    await activeClientRow.getByRole("button", { name: "停用客户端" }).click();
    const disableClientDialog = page
      .getByRole("dialog")
      .filter({ hasText: "撤销该客户端当前所有用户授权" });
    await disableClientDialog
      .getByRole("button", { name: "停用客户端" })
      .click();
    await disableClientDialog
      .getByRole("alert")
      .filter({ hasText: "不会自动重试" })
      .waitFor();
    assert.equal(
      calls.clientDisable.length,
      1,
      "network failure does not automatically resend disable"
    );
    await disableClientDialog
      .getByRole("button", { name: "重新读取状态" })
      .click();
    await disableClientDialog
      .getByText("重新读取已确认客户端仍处于启用状态", { exact: false })
      .waitFor();
    assert.equal(
      calls.clientDisable.length,
      1,
      "state check is a GET, not a repeated DELETE"
    );
    await disableClientDialog
      .getByRole("button", { name: "停用客户端" })
      .click();
    await activeClientRow.getByText("已停用").waitFor();
    assert.equal(
      calls.clientDisable.length,
      2,
      "disable repeats only after an explicit user confirmation"
    );
    await page.getByRole("button", { name: "注册客户端" }).click();
    const createClientDialog = page
      .getByRole("dialog")
      .filter({ hasText: "注册 OAuth 客户端" });
    await createClientDialog.getByLabel("应用名称").fill("新 Workbench 客户端");
    await createClientDialog
      .getByLabel("HTTPS 回调地址")
      .fill("https://app.example.com/oidc/callback");
    incompleteNextClientList = true;
    await createClientDialog
      .getByRole("button", { name: "创建客户端" })
      .click();
    await createClientDialog
      .getByRole("alert")
      .filter({ hasText: "本次未发送创建请求" })
      .waitFor();
    assert.equal(
      calls.createdClient,
      0,
      "incomplete pre-submit snapshot must prevent the POST"
    );
    await createClientDialog
      .getByRole("button", { name: "创建客户端" })
      .click();
    await createClientDialog
      .getByRole("alert")
      .getByRole("button", { name: "刷新列表核对" })
      .click();
    const uncertainClientNotice = page
      .getByRole("alert")
      .filter({ hasText: "发现一条提交后新增的匹配客户端" });
    await uncertainClientNotice.waitFor();
    assert.equal(
      (await uncertainClientNotice.innerText()).includes("请停用该记录"),
      false,
      "uncertain outcome must not advise disabling a pre-existing matching client"
    );
    await uncertainClientNotice
      .getByRole("button", { name: "查看疑似客户端" })
      .click();
    const uncertainClientDetails = page
      .getByRole("dialog")
      .filter({ hasText: "OAuth 客户端详情" });
    assert.equal(
      await uncertainClientDetails.getByLabel("完整 Client ID").inputValue(),
      "ontology_uncertain_123",
      "the checked candidate must be the post-snapshot record"
    );
    assert.equal(
      calls.createdClient,
      1,
      "checking an uncertain create must not replay the POST"
    );
    await page.keyboard.press("Escape");
    await uncertainClientDetails.waitFor({ state: "hidden" });
    await page.getByRole("button", { name: "注册客户端" }).click();
    const successfulCreateDialog = page
      .getByRole("dialog")
      .filter({ hasText: "注册 OAuth 客户端" });
    await successfulCreateDialog
      .getByLabel("应用名称")
      .fill("全新 Workbench 客户端");
    await successfulCreateDialog
      .getByLabel("HTTPS 回调地址")
      .fill("https://app.example.com/other-callback");
    await successfulCreateDialog
      .getByRole("button", { name: "创建客户端" })
      .click();
    const secretDialog = page
      .getByRole("dialog")
      .filter({ hasText: "客户端凭证（仅此一次）" });
    await secretDialog.waitFor();
    assert.equal(
      await secretDialog.getByLabel("Client Secret").getAttribute("type"),
      "password",
      "new secret is hidden initially"
    );
    assert.equal(
      await secretDialog.getByLabel("Client Secret").inputValue(),
      "example-client-secret-once",
      "manual saving remains possible if clipboard access fails"
    );
    await secretDialog
      .getByRole("button", { name: "复制Client Secret" })
      .click();
    await secretDialog
      .getByRole("alert")
      .filter({ hasText: "手动选择并保存" })
      .waitFor();
    await secretDialog.getByRole("button", { name: "已保存，关闭" }).click();
    await secretDialog.waitFor({ state: "detached" });
    assert.equal(calls.createdClient, 2);
    console.log(
      "PASS: disabled client keeps details, full values are accessible, and uncertain writes require a state check before manual retry"
    );

    await page.getByRole("menuitem", { name: "安全审计" }).click();
    await page.getByText("请求身份验证失败").waitFor();
    const auditTable = page.locator(".ant-table");
    assert.equal(
      await auditTable.locator("tbody > tr.ant-table-row").count(),
      20,
      "audit displays 20 while requesting one lookahead record"
    );
    assert.equal(await page.getByRole("button", { name: "查看最新" }).count(), 0,
      "latest shortcut is hidden on the first cursor page");
    const unauthenticatedRow = page
      .getByRole("row")
      .filter({ hasText: "请求身份验证失败" });
    await unauthenticatedRow.getByText("未记录身份").waitFor();
    assert.equal(
      (await auditTable.innerText()).includes("req-smoke-123"),
      false,
      "request id is kept out of the primary table"
    );
    await unauthenticatedRow.getByRole("button", { name: "查看详情" }).click();
    const auditDrawer = page
      .getByRole("dialog")
      .filter({ hasText: "请求身份验证失败" });
    await auditDrawer.getByText("未记录身份").waitFor();
    await auditDrawer.getByText("req-smoke-123").waitFor();
    await auditDrawer.getByText("技术详情（JSON）").click();
    await auditDrawer.locator("pre").getByText("req-smoke-123").waitFor();
    await page.keyboard.press("Escape");
    await auditDrawer.waitFor({ state: "hidden" });
    await page.getByRole("button", { name: "更早记录" }).click();
    await page
      .getByRole("row")
      .filter({ hasText: "用户 ID" })
      .first()
      .waitFor();
    assert.equal(await page.getByRole("button", { name: "查看最新" }).count(), 1,
      "latest shortcut appears while browsing cursor history");
    assert.equal(
      calls.audit.some((call) => call.limit === 21 && call.beforeId === 81),
      true,
      "older cursor uses the last displayed record"
    );
    await page.getByRole("button", { name: "较新记录" }).click();
    await page.getByText("请求身份验证失败").waitFor();
    assert.equal(await page.getByRole("button", { name: "查看最新" }).count(), 0,
      "latest shortcut disappears on the latest cursor page");
    await page.getByRole("button", { name: "更早记录" }).click();
    await page.getByRole("button", { name: "查看最新" }).click();
    await page.getByText("请求身份验证失败").waitFor();
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.locator(".ant-select-selector").nth(0).click();
    await page.getByText("最近 7 天", { exact: true }).click();
    assert.match(
      await page.locator(".ant-select-selector").nth(0).innerText(),
      /最近\s*7\s*天/,
      "quick time selector reflects the selected range"
    );
    await page.locator(".ant-select-selector").nth(1).click();
    await page
      .locator(".ant-select-dropdown .ant-select-item-option-content")
      .filter({ hasText: /^停用账号$/ })
      .click();
    await page.keyboard.press("Escape");
    await page.getByLabel("操作人 ID（完整 UUID）").fill("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    await page.getByRole("button", { name: /查\s*询/ }).click();
    await page.getByRole("row").filter({ hasText: "停用账号" }).waitFor();
    assert.equal(await auditTable.locator("tbody > tr.ant-table-row").count(), 1,
      "server-side audit filtering searches all records before cursor pagination");
    const filteredCall = calls.audit.at(-1);
    assert.deepEqual(filteredCall.eventTypes, ["ACCOUNT_DISABLED"]);
    assert.equal(filteredCall.actorUserId, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    assert.match(filteredCall.startTime, /\+08:00$/);
    assert.match(filteredCall.endTime, /\+08:00$/);
    await page.getByRole("button", { name: /重\s*置/ }).click();
    await page.goBack();
    await page.getByRole("heading", { name: "OAuth 客户端" }).waitFor();
    await page.goForward();
    await page.getByRole("heading", { name: "安全审计" }).waitFor();
    console.log(
      "PASS: audit uses readable summaries, honest actor labels, detail drawers, cursor navigation, and browser history"
    );

    await page.setViewportSize({ width: 390, height: 844 });
    await page.getByRole("button", { name: "打开主导航" }).click();
    const navigationDrawer = page
      .getByRole("dialog")
      .filter({ hasText: "导航" });
    await navigationDrawer.getByRole("menuitem", { name: "安全审计" }).waitFor();
    await page.keyboard.press("Escape");
    await navigationDrawer.waitFor({ state: "hidden" });
    await page.waitForFunction(
      () => document.activeElement?.getAttribute("aria-label") === "打开主导航"
    );
    assert.equal(
      await page.evaluate(() =>
        document.activeElement?.getAttribute("aria-label")
      ),
      "打开主导航",
      "drawer returns focus to its trigger"
    );
    const viewportWidths = await page.evaluate(() => ({
      viewport: window.innerWidth,
      document: document.documentElement.scrollWidth,
    }));
    assert.ok(
      viewportWidths.document <= viewportWidths.viewport,
      `narrow viewport must not scroll horizontally: ${JSON.stringify(
        viewportWidths
      )}`
    );
    const mobileEventFilterWidth = await page
      .locator('.ant-select[aria-label="事件类型"]')
      .evaluate((element) => element.getBoundingClientRect().width);
    assert.ok(
      mobileEventFilterWidth >= 250,
      `mobile event filter must remain operable: width=${mobileEventFilterWidth}`
    );
    await page.screenshot({
      path: "/tmp/ontology-admin-390.png",
      fullPage: true,
    });
    await page.setViewportSize({ width: 640, height: 720 });
    const zoomWidths = await page.evaluate(() => ({
      viewport: window.innerWidth,
      document: document.documentElement.scrollWidth,
    }));
    assert.ok(
      zoomWidths.document <= zoomWidths.viewport,
      `200% effective width must not scroll horizontally: ${JSON.stringify(
        zoomWidths
      )}`
    );
    await page.setViewportSize({ width: 390, height: 844 });
    await page.getByRole("button", { name: "打开主导航" }).click();
    const narrowNav = page.getByRole("dialog").filter({ hasText: "导航" });
    await narrowNav.getByRole("menuitem", { name: "用户管理" }).click();
    await page.getByRole("heading", { name: "用户管理" }).waitFor();
    await page.getByRole("button", { name: "创建用户" }).click();
    const narrowCreateDialog = page
      .getByRole("dialog")
      .filter({ hasText: "创建用户" });
    await narrowCreateDialog.waitFor();
    await assertModalFitsViewport(page, "user creation dialog");
    await narrowCreateDialog.getByRole("button", { name: /取\s*消/ }).click();
    await page
      .getByRole("row")
      .filter({ hasText: "pending_user" })
      .getByRole("button", { name: "重新生成激活链接" })
      .click();
    const narrowCredentialDialog = page
      .getByRole("dialog")
      .filter({ hasText: "新的激活链接已生成" });
    await narrowCredentialDialog.waitFor();
    await assertModalFitsViewport(page, "one-time credential dialog");
    await narrowCredentialDialog
      .getByRole("button", { name: "已保存，关闭" })
      .click();
    currentUserIsAdmin = false;
    await page.goto(`${base}/admin/users`, { waitUntil: "networkidle" });
    await page.getByText("无权访问", { exact: true }).waitFor();
    await page.getByRole("button", { name: "打开主导航" }).click();
    const regularUserNav = page.getByRole("dialog").filter({ hasText: "导航" });
    assert.equal(
      await regularUserNav.getByRole("menuitem", { name: "用户管理" }).count(),
      0,
      "regular users must not see system admin navigation"
    );
    await page.keyboard.press("Escape");
    currentUserIsAdmin = true;
    await page.setContent(
      `<iframe title="Ontology 窄屏嵌入" src="${base}/admin/users" style="display:block;width:390px;height:844px;border:0"></iframe>`
    );
    const embeddedApp = page.frameLocator('iframe[title="Ontology 窄屏嵌入"]');
    await embeddedApp.getByRole("heading", { name: "用户管理" }).waitFor();
    const embeddedWidths = await embeddedApp
      .locator("html")
      .evaluate((html) => ({
        viewport: html.clientWidth,
        document: html.scrollWidth,
      }));
    assert.ok(
      embeddedWidths.document <= embeddedWidths.viewport,
      `narrow iframe must not scroll horizontally: ${JSON.stringify(
        embeddedWidths
      )}`
    );
    await embeddedApp.getByRole("button", { name: "打开主导航" }).click();
    await embeddedApp
      .getByRole("dialog")
      .filter({ hasText: "导航" })
      .getByRole("menuitem", { name: "安全审计" })
      .click();
    await embeddedApp.getByRole("heading", { name: "安全审计" }).waitFor();
    console.log(
      "PASS: mobile navigation restores focus, dialogs fit 390px, and narrow iframe navigation has no page overflow"
    );
  } finally {
    if (browser) await browser.close();
    if (server) server.close();
  }
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
