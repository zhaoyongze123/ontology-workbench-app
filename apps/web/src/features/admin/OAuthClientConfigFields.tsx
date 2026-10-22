import { Checkbox, Form, Input, Space, Typography } from 'antd';
import React from 'react';
import { OAUTH_SCOPE_OPTIONS } from './oauthScopePresentation';

export type OAuthClientConfigurationFields = {
  displayName: string;
  redirectUri: string;
  scopes: string[];
};

/** 创建和编辑共用同一组客户端配置字段，避免校验和帮助信息产生分叉。 */
const OAuthClientConfigFields: React.FC = () => (
  <>
    <Form.Item
      name="displayName"
      label="应用名称"
      rules={[
        { required: true, whitespace: true, message: '请输入应用名称。' },
      ]}
    >
      <Input maxLength={160} />
    </Form.Item>
    <Form.Item
      name="redirectUri"
      label="HTTPS 回调地址"
      extra="必须是完整 HTTPS 地址，不能包含凭据或 URL 片段。"
      rules={[
        { required: true, message: '请输入回调地址。' },
        {
          validator: async (_, value: string) => {
            if (!value) return Promise.resolve();
            try {
              const uri = new URL(value);
              if (
                uri.protocol !== 'https:' ||
                !uri.hostname ||
                uri.username ||
                uri.password ||
                uri.hash ||
                value.length > 2048 ||
                value.includes('*') ||
                /\s/.test(value)
              ) {
                throw new Error('invalid redirect URI');
              }
              return Promise.resolve();
            } catch {
              return Promise.reject(
                new Error(
                  '请输入有效 HTTPS 地址；不允许通配符、凭据或 URL 片段。',
                ),
              );
            }
          },
        },
      ]}
    >
      <Input
        maxLength={2048}
        placeholder="https://app.example.com/oauth/callback"
        autoComplete="url"
      />
    </Form.Item>
    <Form.Item
      name="scopes"
      label="允许申请的权限"
      extra="至少包含基本身份 profile。"
      rules={[
        {
          validator: async (_, values: string[]) =>
            values?.includes('profile')
              ? Promise.resolve()
              : Promise.reject(new Error('至少选择基本身份（profile）。')),
        },
      ]}
    >
      <Checkbox.Group style={{ display: 'grid', gap: 8 }}>
        {OAUTH_SCOPE_OPTIONS.map((scope) => (
          <Checkbox key={scope.value} value={scope.value}>
            <Space size={6} wrap>
              <span>{scope.label}</span>
              <Typography.Text type="secondary" code>
                {scope.description}
              </Typography.Text>
            </Space>
          </Checkbox>
        ))}
      </Checkbox.Group>
    </Form.Item>
  </>
);

export default OAuthClientConfigFields;
