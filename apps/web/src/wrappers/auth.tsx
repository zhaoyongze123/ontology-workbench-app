import AuthFrame from '@/features/auth/AuthFrame';
import { AuthContext } from '@/features/auth/context';
import { getCurrentUser, type InternalUser } from '@/features/auth/service';
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
          const status = (reason as any)?.response?.status;
          setError(status === 401 ? '401' : status === 403
            ? '权限可能已变更，请重新打开项目确认；未提交修改不会自动重试。'
            : (reason as Error)?.message || '无法确认当前登录状态');
          setCanRetry(!status || status >= 500 || status === 429);
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
