import '@fontsource/inter/latin-400.css';
import '@fontsource/inter/latin-500.css';
import '@fontsource/inter/latin-600.css';
import '@fontsource/inter/latin-700.css';
import { RunTimeLayoutConfig, history } from '@umijs/max';
import { App, ConfigProvider, message } from 'antd';
import { appRelativePath } from './config/appBase';
import { csrfRequestOptions } from './features/auth/csrf';
import { normalizeApiError } from './features/errors/apiError';
import { shouldRequestProblemDetails } from './features/errors/requestContract';
import './theme/workbench.less';
import { applyWorkbenchCssVariables, workbenchAntTheme } from './theme/workbenchTheme';

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

let loginRedirectPending = false;
export function onRouteChange({ location }: { location: { pathname: string } }) {
  if (appRelativePath(location.pathname) === '/login') return;
  // 路由状态是登录跳转的事实源；同一轮并发 401 只触发一次导航。
  loginRedirectPending = false;
}

/** 业务 API 明确协商 Problem Details；成功响应的 DTO 结构不受影响。 */
async function errorContractRequestOptions(url: string, options: any) {
  if (!shouldRequestProblemDetails(url)) return { url, options };
  return {
    url,
    options: {
      ...options,
      headers: {
        Accept: 'application/problem+json, application/json;q=0.9',
        ...(options.headers || {}),
      },
    },
  };
}

/** 错误处理 */
const handleError = (error: any, opts?: { skipErrorHandler?: boolean }) => {
  const appError = normalizeApiError(error);
  // 即使局部 UI 接管提示，也先完成错误归一化与安全提示计算。
  error.appError = appError;
  error.message = appError.message;
  if (opts?.skipErrorHandler || appError.kind === 'cancelled') return;

  const location = history.location;
  const pathname = appRelativePath(location.pathname);
  const route = `${pathname}${location.search}${location.hash}`;
  const onLoginPage = pathname === '/login';

  if (appError.status === 401) {
    if (onLoginPage) return;
    if (!loginRedirectPending) {
      loginRedirectPending = true;
      message.warning(appError.message || '登录已过期，请重新登录。');
      // 使用当前 Router 的事实源，不假定应用采用 hash history。
      history.replace(`/login?redirect=${encodeURIComponent(route)}`);
    }
    return;
  }
  if (appError.status === 429) {
    const wait = appError.retryAfterSeconds;
    message.warning(wait === undefined ? appError.message : `${appError.message} ${wait} 秒后可重试。`);
  } else if (appError.status === 503 || appError.kind === 'timeout' || appError.kind === 'network') {
    message.warning(appError.message);
  } else {
    message.error(appError.message);
  }
};

/** 请求拦截器配置 */
export const request = {
  // Umi 的请求拦截器在运行时注册，构建配置里的函数不会进入实际请求链。
  withCredentials: true,
  requestInterceptors: [errorContractRequestOptions, csrfRequestOptions],
  errorHandler: handleError,
  errorConfig: {
    /** 错误抛出 */
    errorThrower: (res: any) => {
      const { success, data, code, message: backendMessage, errorCode, errorMessage } = res;
      if (success === false) {
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
  applyWorkbenchCssVariables();
  return (
    <ConfigProvider theme={workbenchAntTheme}>
      <App>{container}</App>
    </ConfigProvider>
  );
}
