import { request as umiRequest, type RequestOptions } from 'umi';
import { normalizeApiError } from './apiError';

/** 由页面明确承担提示的请求在这里关闭全局呈现，并统一转成 AppError。 */
export async function localRequest<T>(url: string, options: any = {}): Promise<T> {
  try {
    const requestOptions = {
      ...options,
      skipErrorHandler: true,
      getResponse: false,
    } as RequestOptions & { getResponse: false };
    return await umiRequest<T>(url, requestOptions);
  } catch (error) {
    throw normalizeApiError(error);
  }
}
