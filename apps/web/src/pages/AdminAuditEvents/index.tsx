import { useCurrentUser } from '@/features/auth/context';
import { getAuditEvents, type AuditEvent } from '@/features/admin/service';
import { Alert, App, Button, Descriptions, Result, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React, { useEffect, useState } from 'react';

const AdminAuditEventsPage: React.FC = () => {
  const actor = useCurrentUser(); const { message } = App.useApp();
  const [rows, setRows] = useState<AuditEvent[]>([]); const [loading, setLoading] = useState(false);
  const [cursorStack, setCursorStack] = useState<Array<number | undefined>>([undefined]); const [hasNext, setHasNext] = useState(false);
  const [loadError, setLoadError] = useState(''); const beforeId = cursorStack[cursorStack.length - 1];
  const load = async (cursor?: number) => { setLoading(true); setLoadError(''); try { const values = await getAuditEvents(50, cursor); setRows(values); setHasNext(values.length === 50); }
    catch (error) { const status = (error as any)?.response?.status; const text = status === 403 ? '当前账号没有审计查看权限。' : (error as Error)?.message || '审计记录加载失败'; setLoadError(text); }
    finally { setLoading(false); } };
  useEffect(() => { if (actor.systemAdmin) void load(beforeId); }, [actor.systemAdmin, beforeId]);
  if (!actor.systemAdmin) return <Result status="403" title="无权访问" subTitle="此页面仅供系统管理员使用。" />;
  const columns: ColumnsType<AuditEvent> = [
    { title: '时间', dataIndex: 'createdAt', width: 190, render: (value: string | null) => value ? new Date(value).toLocaleString() : '—' },
    { title: '事件', dataIndex: 'eventType', width: 260, render: (value: string) => <Tag>{value}</Tag> },
    { title: '操作者', dataIndex: 'actorUserId', width: 180, render: (value: string | null) => value || '系统' },
    { title: '详情', dataIndex: 'details', render: (value: Record<string, unknown>) => <Typography.Text code style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{JSON.stringify(value || {})}</Typography.Text> },
  ];
  const nextPage = () => { if (!rows.length) return; setCursorStack((stack) => [...stack, rows[rows.length - 1].id]); };
  const previousPage = () => { if (cursorStack.length > 1) setCursorStack((stack) => stack.slice(0, -1)); };
  return <main style={{ padding: 24, maxWidth: 1280, margin: '0 auto' }}>
    <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 18 }}><div><Typography.Title level={3}>安全审计</Typography.Title><Typography.Text type="secondary">记录只读查看；分页按事件 ID 向前读取。</Typography.Text></div><Button onClick={() => void load(beforeId)}>刷新</Button></Space>
    {loadError ? <Alert type="error" showIcon message="审计读取失败" description={loadError} action={<Button onClick={() => void load(beforeId)}>重试</Button>} /> : null}
    <Table<AuditEvent> rowKey="id" columns={columns} dataSource={loadError ? [] : rows} loading={loading} pagination={false} scroll={{ x: 900 }} expandable={{ expandedRowRender: (row) => <Descriptions size="small" column={1} items={[{ key: 'id', label: '事件编号', children: row.id }, { key: 'details', label: '结构化详情', children: <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{JSON.stringify(row.details, null, 2)}</pre> }]} /> }} />
    <Space style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end' }}><Button disabled={cursorStack.length <= 1 || loading} onClick={previousPage}>较新记录</Button><Button type="primary" disabled={!hasNext || loading || Boolean(loadError)} onClick={nextPage}>更早记录</Button></Space>
  </main>;
};
export default AdminAuditEventsPage;
