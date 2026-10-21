import { useCurrentUser } from '@/features/auth/context';
import { createOAuthClient, disableOAuthClient, getOAuthClients, type AppClient, type CreatedAppClient } from '@/features/admin/service';
import { Alert, App, Button, Checkbox, Form, Input, Modal, Popconfirm, Result, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React, { useEffect, useState } from 'react';

const scopes = ['profile', 'ontology.read', 'ontology.write', 'ontology.manage'];
const AdminOAuthClientsPage: React.FC = () => {
  const actor = useCurrentUser(); const { message } = App.useApp();
  const [items, setItems] = useState<AppClient[]>([]); const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [open, setOpen] = useState(false); const [created, setCreated] = useState<CreatedAppClient>();
  const [creating, setCreating] = useState(false);
  const load = async () => { setLoading(true); setLoadError(''); try { setItems(await getOAuthClients()); } catch (error) { setLoadError((error as Error)?.message || '客户端列表读取失败'); } finally { setLoading(false); } };
  useEffect(() => { if (actor.systemAdmin) void load(); }, [actor.systemAdmin]);
  if (!actor.systemAdmin) return <Result status="403" title="无权访问" subTitle="此页面仅供系统管理员使用。" />;
  const columns: ColumnsType<AppClient> = [
    { title: '应用', dataIndex: 'displayName', render: (value: string) => <Typography.Text strong>{value}</Typography.Text> },
    { title: 'Client ID', dataIndex: 'clientId', render: (value: string) => <Typography.Text code copyable>{value}</Typography.Text> },
    { title: '回调地址', dataIndex: 'redirectUri', ellipsis: true },
    { title: '允许范围', dataIndex: 'scopes', render: (values: string[]) => <Space wrap>{values.map((scope) => <Tag key={scope}>{scope}</Tag>)}</Space> },
    { title: '状态', dataIndex: 'enabled', render: (enabled: boolean) => <Tag color={enabled ? 'green' : 'default'}>{enabled ? '启用' : '已停用'}</Tag> },
    { title: '操作', key: 'actions', width: 100, render: (_, item) => item.enabled ? <Popconfirm title="停用此客户端？" description="该客户端当前所有用户授权都会撤销。" onConfirm={async () => { try { await disableOAuthClient(item.id); message.success('客户端已停用，相关授权已撤销'); await load(); } catch (error) { message.error((error as Error)?.message || '停用失败'); } }}><Button danger type="link">停用</Button></Popconfirm> : null },
  ];
  return <main style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
    <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 20 }} align="start"><div><Typography.Title level={3}>OAuth 客户端</Typography.Title><Typography.Text type="secondary">新 Secret 仅在创建完成时展示一次；客户端资料不会包含 Secret。</Typography.Text></div><Button type="primary" onClick={() => setOpen(true)}>注册客户端</Button></Space>
    {loadError ? <Alert style={{ marginBottom: 16 }} type="error" showIcon message="客户端列表读取失败" description={loadError} action={<Button onClick={() => void load()}>重试</Button>} /> : null}
    <Table rowKey="id" columns={columns} dataSource={loadError ? [] : items} loading={loading} locale={{ emptyText: loadError ? '列表暂不可用' : '暂无 OAuth 客户端' }} scroll={{ x: 1000 }} pagination={{ pageSize: 20, showSizeChanger: true }} />
    <Modal title="注册 OAuth 客户端" open={open} onCancel={() => setOpen(false)} footer={null} destroyOnClose>
      <Form layout="vertical" initialValues={{ scopes: ['profile', 'ontology.read'] }} onFinish={async (values: { displayName: string; redirectUri: string; scopes: string[] }) => { setCreating(true); try { const result = await createOAuthClient(values); setCreated(result); setOpen(false); await load(); } catch (error) { message.error((error as Error)?.message || '客户端创建失败'); } finally { setCreating(false); } }}>
        <Form.Item name="displayName" label="应用名称" rules={[{ required: true, message: '请输入应用名称' }]}><Input maxLength={160} /></Form.Item>
        <Form.Item name="redirectUri" label="精确 HTTPS 回调地址" rules={[{ required: true, type: 'url', message: '请输入有效 HTTPS 地址' }]}><Input placeholder="https://app.example.com/oauth/callback" /></Form.Item>
        <Form.Item name="scopes" label="允许申请的权限" rules={[{ required: true, message: '至少选择 profile' }]}><Checkbox.Group options={scopes.map((value) => ({ label: value, value }))} /></Form.Item>
        <Typography.Paragraph type="secondary">服务端要求授权请求包含 profile，并使用授权码 + PKCE。</Typography.Paragraph>
        <Button type="primary" htmlType="submit" loading={creating}>创建客户端</Button>
      </Form>
    </Modal>
    <Modal title="客户端凭证（仅此一次）" open={Boolean(created)} onCancel={() => setCreated(undefined)} footer={<Button onClick={() => setCreated(undefined)}>关闭并清除</Button>} destroyOnClose>
      {created ? <><Typography.Paragraph>请立即将 Secret 存入 Workbench 的受保护配置。关闭窗口后此页面不会保留它。</Typography.Paragraph>
        <Typography.Text>Client ID</Typography.Text><Input value={created.clientId} readOnly />
        <Typography.Text>Client Secret</Typography.Text><Input.Password value={created.clientSecret} readOnly visibilityToggle={false} />
        <Button style={{ marginTop: 12 }} onClick={async () => { try { await navigator.clipboard.writeText(created.clientSecret); message.success('Secret 已复制'); } catch { message.error('复制失败，请在受控环境中手动复制'); } }}>复制 Secret</Button></> : null}
    </Modal>
  </main>;
};
export default AdminOAuthClientsPage;
