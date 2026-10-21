import { normalizeApiError } from '@/features/errors/apiError';
import type { FormInstance } from 'antd';

/** 将统一错误协议中的字段路径放回对应控件，其他错误由调用页面显示。 */
export function applyAdminFormError(
  error: unknown,
  form: FormInstance,
): string {
  const appError = normalizeApiError(error);
  if (appError.fieldErrors.length === 0) return appError.message;

  const fields = appError.fieldErrors
    .filter((field) => Boolean(field.field.trim()))
    .map((field) => ({
      name: field.field.split('.').filter(Boolean),
      errors: [field.message],
    }));
  if (!fields.length) return appError.message;

  form.setFields(fields);
  form.scrollToField(fields[0].name, { block: 'center' });
  return '';
}
