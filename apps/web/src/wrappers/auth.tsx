import AuthFrame from '@/features/auth/AuthFrame';
import { AuthContext } from '@/features/auth/context';
import { getCurrentUser, type InternalUser } from '@/features/auth/service';
import { normalizeApiError } from '@/features/errors/apiError';
import { Alert, Button, Spin } from 'antd';
import React, { useEffect, useState } from 'react';
import { Navigate, Outlet, useLocation } from 'umi';

const AuthWrapper: React.FC = () => {
  const location = useLocation();
  const [user, setUser] = useState<InternalUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [canRetry, setCanRetry] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    setCanRetry(false);
    getCurrentUser()
      .then((current) => active && setUser(current))
      .catch((reason) => {
        if (active) {
          const appError = normalizeApiError(reason);
          setError(appError.status === 401 ? '401' : appError.message);
          setCanRetry(!appError.status || appError.status >= 500 || appError.status === 429
            || appError.kind === 'network' || appError.kind === 'timeout');
        }
      })
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [attempt]);

  if (loading) return <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}><Spin tip="正在验证登录状态…" /></div>;
  if (!user) {
    if (error && !/401|未登录|需要登录/i.test(error)) {
      return <div style={{ padding: 32 }}><Alert type="error" showIcon message="登录状态验证失败" description={error}
        action={canRetry ? <Button onClick={() => setAttempt((value) => value + 1)}>重新验证</Button> : undefined} /></div>;
    }
    const redirect = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate to={`/login?redirect=${encodeURIComponent(redirect)}`} replace />;
  }
  return <AuthContext.Provider value={user}><AuthFrame user={user}><Outlet /></AuthFrame></AuthContext.Provider>;
};

export default AuthWrapper;
