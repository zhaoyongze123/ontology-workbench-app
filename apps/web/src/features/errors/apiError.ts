export type ApiErrorKind = 'http' | 'network' | 'timeout' | 'cancelled';
export type ApiFieldError = { field: string; code?: string; message: string };

export class AppError extends Error {
  readonly kind: ApiErrorKind;
  readonly status?: number;
  readonly errorCode?: string;
  readonly legacyCode?: number;
  readonly requestId?: string;
  readonly fieldErrors: ApiFieldError[];
  readonly retryAfterSeconds?: number;
  readonly writeOutcomeUnknown: boolean;

  constructor(message: string, details: {
    kind: ApiErrorKind;
    status?: number;
    errorCode?: string;
    legacyCode?: number;
    requestId?: string;
    fieldErrors?: ApiFieldError[];
    retryAfterSeconds?: number;
    writeOutcomeUnknown?: boolean;
  }) {
    super(message);
    this.name = 'AppError';
    this.kind = details.kind;
    this.status = details.status;
    this.errorCode = details.errorCode;
    this.legacyCode = details.legacyCode;
    this.requestId = details.requestId;
    this.fieldErrors = details.fieldErrors || [];
    this.retryAfterSeconds = details.retryAfterSeconds;
    this.writeOutcomeUnknown = Boolean(details.writeOutcomeUnknown);
  }
}

const FALLBACK_BY_STATUS: Record<number, string> = {
  400: '请求参数无效，请检查后重试。',
  401: '登录已过期或账号凭证无效。',
  403: '当前账号没有执行此操作的权限。',
  404: '请求的资源不存在或当前账号不可见。',
  405: '不支持该请求方法。',
  406: '当前请求不支持可返回的内容格式。',
  409: '当前状态不允许此操作。',
  413: '请求内容超过允许大小。',
  415: '请求内容格式不受支持。',
  422: '请求未通过业务规则或查询限制。',
  429: '请求过于频繁，请稍后重试。',
  500: '服务器处理失败，请稍后重试。',
  503: '服务暂时不可用，请稍后重试。',
};

const LEGACY_ERROR_MESSAGES: Record<string, string> = {
  invalid_credentials: '用户名或密码错误。',
  token_invalid: '链接已失效或已使用，请联系系统管理员重新生成。',
  too_many_attempts: '尝试次数过多，请稍后重试。',
  too_many_requests: '请求过于频繁，请稍后重试。',
  account_disabled: '账号已停用，请联系系统管理员。',
  account_not_activated: '账号尚未激活，请联系系统管理员。',
  expired_token: '登录已过期，请重新登录。',
  unauthorized: '请先登录或重新登录。',
  forbidden: '当前账号没有执行此操作的权限。',
  csrf_invalid: '请求安全校验未通过，请刷新页面后重试。',
  not_found: '请求的资源不存在或当前账号不可见。',
  conflict: '当前状态不允许此操作。',
  request_unavailable: '服务暂时不可用，请稍后重试。',
  authentication_unavailable: '认证服务暂时不可用，请稍后重试。',
};

function record(value: unknown): Record<string, any> {
  return value !== null && typeof value === 'object' ? value as Record<string, any> : {};
}

function header(headers: unknown, name: string): string | undefined {
  const source = record(headers);
  const getter = source.get;
  if (typeof getter === 'function') {
    const value = getter.call(headers, name);
    if (value !== undefined && value !== null) return String(value);
  }
  const key = Object.keys(source).find((candidate) => candidate.toLowerCase() === name.toLowerCase());
  const value = key ? source[key] : undefined;
  return value === undefined || value === null ? undefined : String(value);
}

function normalizedFields(value: unknown): ApiFieldError[] {
  if (Array.isArray(value)) {
    return value.filter((item) => typeof item?.field === 'string' && typeof item?.message === 'string')
      .map((item) => ({ field: item.field, code: typeof item.code === 'string' ? item.code : undefined, message: item.message }));
  }
  const fields = record(value);
  return Object.entries(fields).filter(([, message]) => typeof message === 'string')
    .map(([field, message]) => ({ field, message: message as string }));
}

function retryAfterSeconds(headers: unknown): number | undefined {
  const raw = header(headers, 'Retry-After');
  if (!raw) return undefined;
  const seconds = Number(raw);
  if (Number.isFinite(seconds)) return Math.max(0, Math.ceil(seconds));
  const at = Date.parse(raw);
  return Number.isNaN(at) ? undefined : Math.max(0, Math.ceil((at - Date.now()) / 1000));
}

function fallback(status?: number, legacyError?: unknown): string {
  if (typeof legacyError === 'string') {
    const known = LEGACY_ERROR_MESSAGES[legacyError.toLowerCase()];
    if (known) return known;
  }
  return status ? FALLBACK_BY_STATUS[status] || `请求失败（${status}），请稍后重试。`
    : '网络暂时不可用，请检查连接后重试。';
}

function safeServerMessage(...values: unknown[]): string | undefined {
  for (const value of values) {
    if (typeof value !== 'string' || !value.trim()) continue;
    const text = value.trim();
    // 旧协议中纯机器码不能作为面向用户的文案显示。
    if (/^[a-z][a-z0-9_-]*$/i.test(text)) continue;
    return text;
  }
  return undefined;
}

function isWriteMethod(method: unknown): boolean {
  const normalized = typeof method === 'string' ? method.toUpperCase() : 'GET';
  return !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(normalized);
}

/** 将 Problem Details、旧 ApiResult、{error}、网络和取消错误归一成一个前端类型。 */
export function normalizeApiError(input: unknown): AppError {
  if (input instanceof AppError) return input;
  const error = record(input);
  const response = record(error.response);
  const data = record(response.data);
  const info = record(error.info);
  const status = typeof response.status === 'number' ? response.status
    : error.name === 'BizError' ? 200 : undefined;
  const headers = response.headers;
  const infoData = record(info.data);
  const legacyError = data.error ?? info.error ?? infoData.error;
  const cancelled = error.code === 'ERR_CANCELED' || error.__CANCEL__ === true
    || error.name === 'AbortError' || error.name === 'CanceledError';
  const timeout = ['ECONNABORTED', 'ETIMEDOUT', 'ERR_TIMEOUT'].includes(String(error.code || ''))
    || error.name === 'TimeoutError';
  const kind: ApiErrorKind = cancelled ? 'cancelled' : status !== undefined ? 'http' : timeout ? 'timeout' : 'network';
  const requestId = typeof data.requestId === 'string' ? data.requestId
    : typeof infoData.requestId === 'string' ? infoData.requestId
    : header(headers, 'X-Request-ID');
  const legacyErrorCode = typeof legacyError === 'string' && legacyError.toLowerCase() === 'token_invalid'
    ? 'ACCOUNT_ACTION_INVALID' : undefined;
  const errorCode = typeof data.errorCode === 'string' ? data.errorCode
    : typeof info.errorCode === 'string' ? info.errorCode : legacyErrorCode;
  const rawLegacyCode = data.code ?? info.errorCode ?? infoData.code;
  const legacyCode = typeof rawLegacyCode === 'number' ? rawLegacyCode : undefined;
  const legacyData = data.success === false ? data.data : infoData.success === false ? infoData.data : undefined;
  const isLegacyValidation = legacyCode === 4002 || errorCode === 'VALIDATION_FAILED';
  const fields = normalizedFields(data.errors ?? info.errors ?? (isLegacyValidation ? legacyData : undefined));
  let message = safeServerMessage(data.detail, data.message, data.errorMessage, info.errorMessage,
    infoData.detail, infoData.message, infoData.errorMessage);
  if (!message && kind === 'cancelled') message = '请求已取消。';
  // 写请求超时可能已经在服务端提交；优先提示核对结果，避免误导用户直接重放。
  const writeOutcomeUnknown = status === undefined && isWriteMethod(record(error.config).method)
    && (kind === 'network' || kind === 'timeout');
  if (!message && writeOutcomeUnknown) {
    message = '提交结果暂未确认，请先核对当前状态后再重试。';
  }
  if (!message && kind === 'timeout') message = '请求超时，请稍后重试。';
  message ||= error.name === 'BizError'
    ? '操作未能完成，请检查输入后重试。'
    : fallback(status, legacyError);
  if (status && status >= 500 && requestId && !message.includes(requestId)) {
    message = `${message} 请求编号：${requestId}`;
  }
  return new AppError(message, {
    kind,
    status,
    errorCode,
    legacyCode,
    requestId,
    fieldErrors: fields,
    retryAfterSeconds: retryAfterSeconds(headers),
    writeOutcomeUnknown,
  });
}
