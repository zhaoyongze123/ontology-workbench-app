import { appRelativePath } from '@/config/appBase';
import { getAuthOptions, login } from '@/features/auth/service';
import { LockOutlined, LoginOutlined } from '@ant-design/icons';
import { Alert, Button, Card, Form, Input, Spin, Typography } from 'antd';
import React, { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'umi';
import './index.less';

const WORKBENCH_AUTH_URL = '/api/v1/oauth2/authorization/workbench';

const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [options, setOptions] = useState<{ passwordEnabled: boolean; workbenchEnabled: boolean } | null>(null);
  const [optionsError, setOptionsError] = useState('');
  const [loading, setLoading] = useState(false);
  const [loginError, setLoginError] = useState('');

  useEffect(() => {
    let active = true;
    getAuthOptions()
      .then((value) => active && setOptions(value))
      .catch((error) => active && setOptionsError((error as Error)?.message || '暂时无法读取登录方式'));
    return () => {
      active = false;
    };
  }, []);

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true);
    setLoginError('');
    try {
      await login(values.username, values.password);
      const requested = appRelativePath(searchParams.get('redirect') || '/home');
      const safeDestination = requested.startsWith('/') && !requested.startsWith('//')
        && !requested.startsWith('/login') ? requested : '/home';
      navigate(safeDestination, { replace: true });
    } catch (error) {
      const response = (error as any)?.response;
      const code = response?.data?.error;
      setLoginError(code === 'invalid_credentials'
        ? '用户名或密码错误'
        : code === 'too_many_attempts'
          ? '尝试次数过多，请稍后再试'
          : response?.data?.message || (error as Error)?.message || '登录失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  const startWorkbench = () => window.location.assign(WORKBENCH_AUTH_URL);
  const openWorkbenchWindow = () => {
    // 备用窗口只访问固定同源授权入口，并使用 noopener 阻断窗口反向引用。
    window.open(new URL(WORKBENCH_AUTH_URL, window.location.origin).toString(), '_blank', 'noopener,noreferrer');
  };

  return (
    <main className="login-container">
      <Card className="login-card" bordered>
        <div className="login-brand">
          <Typography.Text className="login-kicker">ONTOLOGY WORKSPACE</Typography.Text>
          <Typography.Title level={3}>登录本体管理</Typography.Title>
          <Typography.Paragraph type="secondary">使用已启用的内部账号继续工作</Typography.Paragraph>
        </div>

        {optionsError ? <Alert type="error" showIcon message={optionsError} /> : null}
        {!options && !optionsError ? <div className="login-options-loading"><Spin size="small" /> 正在读取可用登录方式</div> : null}

        <Form layout="vertical" name="login" onFinish={onFinish} autoComplete="on">
          <Form.Item label="用户名" name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input autoComplete="username" placeholder="输入用户名" size="large" />
          </Form.Item>
          <Form.Item label="密码" name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password autoComplete="current-password" placeholder="输入密码" size="large" />
          </Form.Item>
          {loginError ? <Alert className="login-error" type="error" showIcon message={loginError} /> : null}
          <Button
            type="primary"
            htmlType="submit"
            icon={<LoginOutlined />}
            size="large"
            block
            loading={loading}
            disabled={!options?.passwordEnabled}
          >
            账号密码登录
          </Button>
        </Form>

        <div className="login-divider"><span>或</span></div>
        <Button
          icon={<LockOutlined />}
          size="large"
          block
          disabled={!options?.workbenchEnabled}
          onClick={startWorkbench}
        >
          Workbench 登录
        </Button>
        {options?.workbenchEnabled ? (
          <Button type="link" className="login-new-window" onClick={openWorkbenchWindow}>
            第三方 Cookie 受限？在新窗口继续
          </Button>
        ) : (
          <Typography.Paragraph className="login-disabled-note" type="secondary">
            Workbench 登录暂不可用
          </Typography.Paragraph>
        )}
      </Card>
    </main>
  );
};

export default LoginPage;
