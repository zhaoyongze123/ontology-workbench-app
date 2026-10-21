import { useCurrentUser } from '@/features/auth/context';
import { getAppAuthorizations, revokeAppAuthorization, type AppAuthorization } from '@/features/admin/service';
import { Alert, App, Button, Popconfirm, Result, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React, { useEffect, useState } from 'react';

const AppAuthorizationsPage: React.FC = () => {
  const user = useCurrentUser(); const { message } = App.useApp();
  const [items, setItems] = useState<AppAuthorization[]>([]); const [loading, setLoading] = useState(false); const [loadError, setLoadError] = useState('');
  const load = async () => { setLoading(true); setLoadError(''); try { setItems(await getAppAuthorizations()); } catch (error) { setLoadError((error as Error)?.message || '授权列表读取失败'); } finally { setLoading(false); } };
  useEffect(() => { void load(); }, []);
  const columns: ColumnsType<AppAuthorization> = [
    { title: '应用', dataIndex: 'applicationName', render: (value: string) => <Typography.Text strong>{value}</Typography.Text> },
    { title: '权限范围', dataIndex: 'scopes', render: (value: string) => <Space wrap>{(value || '').split(/\s+/).filter(Boolean).map((scope) => <Tag key={scope}>{scope}</Tag>)}</Space> },
    { title: '授权时间', dataIndex: 'authorizedAt', render: (value: string) => new Date(value).toLocaleString() },
    { title: '有效至', dataIndex: 'absoluteExpiresAt', render: (value: string) => new Date(value).toLocaleString() },
    { title: '状态', dataIndex: 'revokedAt', render: (value: string | null, item) => value ? <Tag>已撤销</Tag> : new Date(item.absoluteExpiresAt).getTime() <= Date.now() ? <Tag>已到期</Tag> : <Tag color="green">有效</Tag> },
    { title: '操作', key: 'action', width: 100, render: (_, item) => !item.revokedAt && new Date(item.absoluteExpiresAt).getTime() > Date.now() ? <Popconfirm title="撤销此应用授权？" description="该连接的访问和刷新令牌将失效。" onConfirm={async () => { try { await revokeAppAuthorization(item.id); message.success('授权已撤销'); await load(); } catch (error) { message.error((error as Error)?.message || '撤销失败'); } }}><Button danger type="link">撤销</Button></Popconfirm> : null },
  ];
  return <main style={{ padding: 24, maxWidth: 1180, margin: '0 auto' }}><Typography.Title level={3}>我的 App 授权</Typography.Title><Typography.Paragraph type="secondary">管理由你本人批准的应用连接。撤销结果由服务端授权状态确认。</Typography.Paragraph>
    {loadError ? <Alert style={{ marginBottom: 16 }} type="error" showIcon message="授权列表读取失败" description={loadError} action={<Button onClick={() => void load()}>重试</Button>} /> : null}
    <Table rowKey="id" columns={columns} dataSource={loadError ? [] : items} loading={loading} scroll={{ x: 850 }} pagination={{ pageSize: 20, showSizeChanger: true }} locale={{ emptyText: loadError ? '列表暂不可用' : '当前没有 App 授权记录' }} />
  </main>;
};
export default AppAuthorizationsPage;
