import { RunTimeLayoutConfig, history } from '@umijs/max';
import { App, ConfigProvider, message } from 'antd';
import { appRelativePath } from './config/appBase';
import { csrfRequestOptions } from './features/auth/csrf';

/** 运行时配置 */
export const layout: RunTimeLayoutConfig = () => {
  return {
    /** 默认不展示 layout */
    pure: true,
    /** 不展示菜单 */
    menuRender: false,
    /** 不展示菜单顶栏 */
    headerRender: false,
    /** 不展示页脚 */
    footerRender: false,
    /** 不自动设置标题 */
    pageTitleRender: false,
  };
};

/** 全局初始化数据配置 */
export async function getInitialState() {
  return {
    name: '用户',
  };
}

/** 将常见服务端错误码转换为用户可理解的提示，不把内部枚举原样显示。 */
function safeBackendMessage(value: unknown): string | undefined {
  if (typeof value !== 'string' || !value.trim()) return undefined;
  const text = value.trim();
  const code = text.toLowerCase();
  const knownCodes: Record<string, string> = {
    invalid_credentials: '用户名或密码错误',
    too_many_attempts: '尝试次数过多，请稍后重试。',
    account_disabled: '账号已停用，请联系系统管理员。',
    account_not_activated: '账号尚未激活，请联系系统管理员。',
    expired_token: '登录已过期，请重新登录。',
  };
  if (knownCodes[code]) return knownCodes[code];
  // 形如 forbidden、access_denied 的单词是协议/内部代码，交给状态码映射中文化。
  if (/^[a-z][a-z0-9_-]*$/i.test(text)) return undefined;
  return text;
}

/** 错误处理 */
const handleError = (error: any, opts?: { skipErrorHandler?: boolean }) => {
  if (opts?.skipErrorHandler) return;
  if (!error.response) {
    if (error.name === 'BizError') {
      error.message = safeBackendMessage(error.message) || '操作失败，请稍后重试。';
      message.error(error.message);
      return;
    }
    error.message = '网络暂时不可用，请检查连接后重试。';
    message.error(error.message);
    return;
  }

  const { status, data } = error.response;
  const location = history.location;
  const pathname = appRelativePath(location.pathname);
  const route = `${pathname}${location.search}${location.hash}`;
  const onLoginPage = pathname === '/login';

  if (status === 401) {
    error.message = '登录已过期或账号已停用，请重新登录。';
    message.warning(error.message);
    if (!onLoginPage) {
      // 使用当前 Router 的事实源，不假定应用采用 hash history。
      history.replace(`/login?redirect=${encodeURIComponent(route)}`);
    }
    return;
  }
  const backendMessage = safeBackendMessage(data?.message) || safeBackendMessage(data?.error);

  switch (status) {
    case 400:
      error.message = backendMessage || '请求参数无效';
      message.error(error.message);
      break;
    case 403:
      error.message = `权限可能已变更，请重新打开项目确认；未提交修改不会自动重试。${backendMessage ? `（${backendMessage}）` : ''}`;
      message.error(error.message);
      break;
    case 404:
      error.message = backendMessage || '请求的资源不存在';
      message.error(error.message);
      break;
    case 500:
      error.message = backendMessage || '服务器暂时不可用，请稍后重试。';
      message.error(error.message);
      break;
    case 429:
      error.message = backendMessage || '请求过于频繁，请稍后重试。';
      message.warning(error.message);
      break;
    default:
      error.message = backendMessage || `请求失败（${status}），请稍后重试。`;
      message.error(error.message);
  }
};

/** 请求拦截器配置 */
export const request = {
  // Umi 的请求拦截器在运行时注册，构建配置里的函数不会进入实际请求链。
  withCredentials: true,
  requestInterceptors: [csrfRequestOptions],
  errorHandler: handleError,
  errorConfig: {
    /** 错误抛出 */
    errorThrower: (res: any) => {
      const { success, data, code, message: backendMessage, errorCode, errorMessage } = res;
      if (!success) {
        const error: any = new Error(errorMessage || backendMessage || '操作失败');
        error.name = 'BizError';
        error.info = { errorCode: errorCode ?? code, errorMessage: errorMessage || backendMessage, data };
        throw error;
      }
    },
    /** 错误接收及处理 */
    errorHandler: handleError,
  },
  paramsSerializer: (params: any) => {
    const searchParams = new URLSearchParams();
    Object.keys(params).forEach((key) => {
      const value = params[key];
      if (Array.isArray(value)) {
        // 数组参数：重复参数名，不加 []
        value.forEach((item) => {
          searchParams.append(key, item);
        });
      } else if (value !== undefined && value !== null) {
        searchParams.append(key, value);
      }
    });
    return searchParams.toString();
  },
};

/** 全局包裹组件，提供App Context */
export function rootContainer(container: React.ReactNode) {
  return (
    <ConfigProvider
      theme={{
        token: {
          colorLink: '#570CF4',
          colorLinkActive: '#570CF4',
          colorLinkHover: '#570cf482',
          colorPrimary: '#570CF4',
        },
      }}
    >
      <App>{container}</App>
    </ConfigProvider>
  );
}
