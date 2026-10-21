const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const ts = require('typescript');

function loadTsModule(relativePath) {
  const source = fs.readFileSync(path.join(__dirname, relativePath), 'utf8');
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const loadedModule = { exports: {} };
  new Function('module', 'exports', compiled)(loadedModule, loadedModule.exports);
  return loadedModule.exports;
}

const { normalizeApiError } = loadTsModule('../src/features/errors/apiError.ts');
const { shouldRequestProblemDetails } = loadTsModule('../src/features/errors/requestContract.ts');

test('negotiates Problem Details for business APIs but keeps OAuth and discovery endpoints native', () => {
  assert.equal(shouldRequestProblemDetails('/api/v1/graph/page'), true);
  assert.equal(shouldRequestProblemDetails('/api/v1/admin/oauth/clients'), true);
  assert.equal(shouldRequestProblemDetails('/api/v1/oidc/app/userinfo'), false);
  assert.equal(shouldRequestProblemDetails('/api/v1/oauth2/token'), false);
  assert.equal(shouldRequestProblemDetails('/api/v1/.well-known/openid-configuration'), false);
  assert.equal(shouldRequestProblemDetails('/oauth2/authorization/workbench'), false);
});

test('normalizes Problem Details and keeps the response request id', () => {
  const error = normalizeApiError({
    response: {
      status: 503,
      headers: { 'X-Request-ID': 'request-123' },
      data: { status: 503, detail: '依赖服务暂时不可用。', errorCode: 'DEPENDENCY_UNAVAILABLE' },
    },
  });

  assert.equal(error.kind, 'http');
  assert.equal(error.status, 503);
  assert.equal(error.errorCode, 'DEPENDENCY_UNAVAILABLE');
  assert.equal(error.requestId, 'request-123');
  assert.match(error.message, /request-123/);
});

test('normalizes the legacy ApiResult error and numeric code', () => {
  const error = normalizeApiError({
    name: 'BizError',
    info: { errorCode: 5002, errorMessage: '当前状态冲突。', data: { success: false, code: 5002 } },
  });

  assert.equal(error.legacyCode, 5002);
  assert.equal(error.message, '当前状态冲突。');
});

test('keeps legacy validation field messages without rejected values', () => {
  const error = normalizeApiError({
    name: 'BizError',
    info: {
      errorCode: 4002,
      errorMessage: '参数校验失败。',
      data: { success: false, code: 4002, data: { username: '用户名不能为空。' } },
    },
  });

  assert.deepEqual(error.fieldErrors, [{ field: 'username', message: '用户名不能为空。' }]);
});

test('does not turn a legacy error object into arbitrary field errors', () => {
  const error = normalizeApiError({
    response: { status: 500, data: { success: false, data: { query: 'MATCH (n)' } } },
  });

  assert.deepEqual(error.fieldErrors, []);
  assert.equal(error.message, '服务器处理失败，请稍后重试。');
});

test('reports an unresolved write outcome after a network failure', () => {
  const error = normalizeApiError({ name: 'Error', config: { method: 'POST' } });

  assert.equal(error.kind, 'network');
  assert.equal(error.writeOutcomeUnknown, true);
  assert.match(error.message, /结果暂未确认/);
});

test('does not present a cancelled request as a network failure', () => {
  const error = normalizeApiError({ name: 'CanceledError', code: 'ERR_CANCELED' });

  assert.equal(error.kind, 'cancelled');
  assert.equal(error.message, '请求已取消。');
});
