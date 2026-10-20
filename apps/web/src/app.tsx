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

/** 错误处理 */
const handleError = (error: any) => {
  if (!error.response) {
    if (error.name === 'BizError') {
      message.error(error.message || '操作失败');
      return;
    }
    message.error('请检查您的网络连接');
    return;
  }

  const { status, data } = error.response;
  const location = history.location;
  const pathname = appRelativePath(location.pathname);
  const route = `${pathname}${location.search}${location.hash}`;
  const onLoginPage = pathname === '/login';

  if (status === 401) {
    if (!onLoginPage) {
      // 使用当前 Router 的事实源，不假定应用采用 hash history。
      history.replace(`/login?redirect=${encodeURIComponent(route)}`);
    }
    return;
  }
  const backendMessage = data?.message || data?.error;

  switch (status) {
    case 400:
      message.error(backendMessage || '请求参数无效');
      break;
    case 403:
      message.error(backendMessage || '您没有权限访问该资源');
      break;
    case 404:
      message.error(backendMessage || '请求的资源不存在');
      break;
    case 500:
      message.error(backendMessage || '服务器发生错误');
      break;
    default:
      message.error(backendMessage || `错误 ${status}：请求发生错误`);
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
