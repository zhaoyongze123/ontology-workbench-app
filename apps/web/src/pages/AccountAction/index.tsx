import { appRelativePath } from '@/config/appBase';
import { inspectAccountAction, submitAccountAction, type AccountActionPurpose } from '@/features/auth/accountActions';
import { Button, Card, Form, Input, Result, Spin, Typography } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'umi';

const AccountActionPage: React.FC = () => {
  const navigate = useNavigate();
  const [purpose, setPurpose] = useState<AccountActionPurpose>();
  const [token, setToken] = useState('');
  const [state, setState] = useState<'checking' | 'ready' | 'invalid' | 'done' | 'error'>('checking');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const initialToken = useRef(typeof window === 'undefined' ? '' : new URLSearchParams(window.location.hash.slice(1)).get('token') || '');

  useEffect(() => {
    // 链接凭证只从 fragment 读取；先从地址栏移除，再以 POST 交给服务端核验。
    const suppliedToken = initialToken.current;
    window.history.replaceState(window.history.state, '', `${window.location.pathname}${window.location.search}`);
    if (!suppliedToken) { setState('invalid'); return; }
    let active = true;
    setToken(suppliedToken);
    inspectAccountAction(suppliedToken).then((result) => {
      if (!active) return;
      if (!result.valid) { setState('invalid'); return; }
      setPurpose(result.purpose);
      setState('ready');
    }).catch(() => { if (active) setState('error'); });
    return () => { active = false; };
  }, []);

  const finish = async (values: { password: string; confirmPassword: string }) => {
    if (!purpose) return;
    setBusy(true); setError('');
    try {
      await submitAccountAction(purpose, token, values.password);
      setToken('');
      setState('done');
    } catch (reason) {
      const status = (reason as any)?.response?.status;
      setError(status === 400 || status === 409 ? '链接已失效或已使用，请联系系统管理员重新生成。'
        : (reason as Error)?.message || '提交失败，请稍后重试。');
    } finally { setBusy(false); }
  };

  const title = purpose === 'ACTIVATE' ? '设置账号密码' : '重置账号密码';
  if (state === 'checking') return <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}><Spin tip="正在核验链接…" /></div>;
  if (state === 'invalid' || state === 'error' || state === 'done') return <main style={{ maxWidth: 480, margin: '10vh auto', padding: 16 }}>
    <Result status={state === 'done' ? 'success' : state === 'invalid' ? 'warning' : 'error'}
      title={state === 'done' ? '密码已设置' : state === 'invalid' ? '链接无效或已过期' : '暂时无法核验链接'}
      subTitle={state === 'done' ? '请使用新密码登录。' : state === 'invalid' ? '请联系系统管理员重新生成链接。' : '请检查网络后重新打开链接。'}
      extra={state === 'done' ? <Button type="primary" onClick={() => navigate(appRelativePath('/login'), { replace: true })}>前往登录</Button> : undefined} />
  </main>;
  return <main style={{ maxWidth: 440, margin: '10vh auto', padding: 16 }}>
    <Card bordered><Typography.Title level={3}>{title}</Typography.Title>
      <Typography.Paragraph type="secondary">密码由服务端安全保存；链接仅在提交成功时使用。</Typography.Paragraph>
      <Form layout="vertical" onFinish={finish}>
        <Form.Item name="password" label="新密码" rules={[{ required: true, message: '请输入新密码' }, { min: 12, message: '密码至少需要 12 个字符' }, { max: 1024, message: '密码不能超过 1024 个字符' }]}>
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        <Form.Item name="confirmPassword" label="确认新密码" dependencies={['password']}
          rules={[{ required: true, message: '请再次输入新密码' }, ({ getFieldValue }) => ({ validator(_, value) {
            return !value || getFieldValue('password') === value ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致'));
          } })]}>
          <Input.Password autoComplete="new-password" />
        </Form.Item>
        {error ? <Typography.Text type="danger">{error}</Typography.Text> : null}
        <Button type="primary" htmlType="submit" loading={busy} block>确认设置</Button>
      </Form>
    </Card>
  </main>;
};
export default AccountActionPage;
