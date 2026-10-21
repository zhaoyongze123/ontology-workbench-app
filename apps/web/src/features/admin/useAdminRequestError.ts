import { normalizeApiError } from '@/features/errors/apiError';
import { useCallback, useRef } from 'react';
import { useLocation, useNavigate } from 'umi';

export type AdminRequestFailure = {
  message: string;
  accessLost: boolean;
  redirectedToLogin: boolean;
};

/** 管理服务走页面内错误呈现；401 仍回到登录，403 清空已授权数据。 */
export function useAdminRequestError() {
  const navigate = useNavigate();
  const location = useLocation();
  const redirecting = useRef(false);

  return useCallback(
    (error: unknown): AdminRequestFailure => {
      const appError = normalizeApiError(error);
      if (appError.status === 401) {
        if (!redirecting.current) {
          redirecting.current = true;
          const returnTo = `${location.pathname}${location.search}${location.hash}`;
          navigate(`/login?redirect=${encodeURIComponent(returnTo)}`, {
            replace: true,
          });
        }
        return {
          message: appError.message,
          accessLost: true,
          redirectedToLogin: true,
        };
      }

      return {
        message: appError.message,
        accessLost: appError.status === 403,
        redirectedToLogin: false,
      };
    },
    [location.hash, location.pathname, location.search, navigate],
  );
}
