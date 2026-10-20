import type { InternalUser } from './service';
import React, { createContext, useContext } from 'react';

export const AuthContext = createContext<InternalUser | null>(null);

export function useCurrentUser(): InternalUser {
  const user = useContext(AuthContext);
  if (!user) throw new Error('当前页面需要已验证的登录会话');
  return user;
}
