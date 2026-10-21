import { useCurrentUser } from '@/features/auth/context';
import { createActivationLink, createResetLink, createUser, getUsers, setUserEnabled, type ManagedUser } from '@/features/admin/service';
import { Alert, App, Button, Form, Input, Modal, Result, Space, Switch, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React, { useEffect, useState } from 'react';

const AdminUsersPage: React.FC = () => {
  const actor = useCurrentUser(); const { message } = App.useApp();
  const [rows, setRows] = useState<ManagedUser[]>([]); const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0); const [size, setSize] = useState(20); const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [createOpen, setCreateOpen] = useState(false); const [createBusy, setCreateBusy] = useState(false);
  const [oneTimeUrl, setOneTimeUrl] = useState(''); const [oneTimeTitle, setOneTimeTitle] = useState('');
  const load = async (nextPage = page, nextSize = size) => {
    setLoading(true); setLoadError('');
    try { const result = await getUsers(nextPage, nextSize); setRows(result.items || []); setTotal(result.total || 0); setPage(result.page); setSize(result.size); }
    catch (error) { setLoadError((error as Error)?.message || '用户列表加载失败'); }
    finally { setLoading(false); }
  };
  useEffect(() => { if (actor.systemAdmin) void load(0, size); }, [actor.systemAdmin]);
  if (!actor.systemAdmin) return <Result status="403" title="无权访问" subTitle="此页面仅供系统管理员使用。" />;

  const showLink = (title: string, url: string) => { setOneTimeTitle(title); setOneTimeUrl(url); };
  const columns: ColumnsType<ManagedUser> = [
    { title: '用户', key: 'name', render: (_, user) => <Space direction="vertical" size={0}><Typography.Text strong>{user.displayName || user.username}</Typography.Text><Typography.Text type="secondary">{user.username}</Typography.Text></Space> },
    { title: '状态', key: 'state', render: (_, user) => <Space><Tag color={user.passwordActivated ? 'green' : 'gold'}>{user.passwordActivated ? '已激活' : '待激活'}</Tag>{user.systemAdmin ? <Tag color="blue">系统管理员</Tag> : null}</Space> },
    { title: '启用', dataIndex: 'enabled', width: 92, render: (enabled: boolean, user) => <Switch checked={enabled} aria-label={`${user.username}账号启用`} onChange={async (value) => { try { await setUserEnabled(user.id, value); message.success(value ? '账号已启用' : '账号已停用'); await load(); } catch (error) { message.error((error as Error)?.message || '账号状态更新失败'); } }} /> },
    { title: '创建时间', dataIndex: 'createTime', width: 180, render: (value: string) => value ? new Date(value).toLocaleString() : '—' },
    { title: '操作', key: 'actions', width: 160, render: (_, user) => user.passwordActivated
      ? <Button type="link" onClick={async () => { try { const result = await createResetLink(user.id); showLink('一次性密码重置链接', result.resetUrl); } catch (error) { message.error((error as Error)?.message || '重置链接生成失败'); } }}>重置密码</Button>
      : <Button type="link" onClick={async () => { try { const result = await createActivationLink(user.id); showLink('重新生成的一次性激活链接（旧链接已失效）', result.activationUrl); } catch (error) { message.error((error as Error)?.message || '激活链接生成失败'); } }}>重发激活链接</Button> },
  ];
  return <main style={{ padding: 24, maxWidth: 1180, margin: '0 auto' }}>
    <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 20 }} align="start"><div><Typography.Title level={3}>用户管理</Typography.Title><Typography.Text type="secondary">账号由管理员开通；激活链接仅在创建响应中展示。</Typography.Text></div><Button type="primary" onClick={() => setCreateOpen(true)}>开通用户</Button></Space>
    {loadError ? <Alert style={{ marginBottom: 16 }} type="error" showIcon message="用户列表读取失败" description={loadError} action={<Button onClick={() => void load()}>重试</Button>} /> : null}
    <Table rowKey="id" columns={columns} dataSource={loadError ? [] : rows} loading={loading} locale={{ emptyText: loadError ? '列表暂不可用' : '暂无用户' }} scroll={{ x: 720 }} pagination={{ current: page + 1, pageSize: size, total, showSizeChanger: true, onChange: (current, nextSize) => void load(current - 1, nextSize) }} />
    <Modal title="开通内部账号" open={createOpen} onCancel={() => setCreateOpen(false)} footer={null} destroyOnClose>
      <Form layout="vertical" onFinish={async (values: { username: string; displayName: string }) => { setCreateBusy(true); try { const result = await createUser(values.username, values.displayName); setCreateOpen(false); showLink('一次性账号激活链接', result.activationUrl); await load(0, size); } catch (error) { message.error((error as Error)?.message || '账号创建失败'); } finally { setCreateBusy(false); } }}>
        <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}><Input autoComplete="off" maxLength={80} /></Form.Item>
        <Form.Item name="displayName" label="显示名称" rules={[{ required: true, message: '请输入显示名称' }]}><Input maxLength={120} /></Form.Item>
        <Button type="primary" htmlType="submit" loading={createBusy}>创建并生成激活链接</Button>
      </Form>
    </Modal>
    <Modal title={oneTimeTitle} open={Boolean(oneTimeUrl)} onCancel={() => setOneTimeUrl('')} footer={<Button onClick={() => setOneTimeUrl('')}>关闭</Button>} destroyOnClose>
      <Typography.Paragraph>此链接只展示一次。请通过已核实身份的安全渠道交付；关闭后页面不会保留链接。</Typography.Paragraph>
      <Input.TextArea value={oneTimeUrl} readOnly autoSize={{ minRows: 2, maxRows: 5 }} onFocus={(event) => event.currentTarget.select()} />
      <Button style={{ marginTop: 12 }} onClick={async () => { await navigator.clipboard.writeText(oneTimeUrl); message.success('已复制一次性链接'); }}>复制链接</Button>
    </Modal>
  </main>;
};
export default AdminUsersPage;
