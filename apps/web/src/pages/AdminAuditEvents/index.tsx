import AdminPage from '@/features/admin/AdminPage';
import {
  presentAuditActor,
  presentAuditEvent,
} from '@/features/admin/auditPresentation';
import {
  ADMIN_TIME_ZONE_LABEL,
  formatAdminDateTime,
} from '@/features/admin/dateTime';
import { getAuditEvents, type AuditEvent } from '@/features/admin/service';
import { useAdminRequestError } from '@/features/admin/useAdminRequestError';
import { useCurrentUser } from '@/features/auth/context';
import {
  Alert,
  Button,
  Collapse,
  Descriptions,
  Drawer,
  Empty,
  Result,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React, { useEffect, useRef, useState } from 'react';

const PAGE_SIZE = 50;
const REQUEST_LIMIT = PAGE_SIZE + 1;

function stringDetail(
  details: Record<string, unknown>,
  field: string,
): string | undefined {
  const value = details[field];
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

const AdminAuditEventsPage: React.FC = () => {
  const actor = useCurrentUser();
  const resolveFailure = useAdminRequestError();
  const [rows, setRows] = useState<AuditEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [hasLoaded, setHasLoaded] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [cursorStack, setCursorStack] = useState<Array<number | undefined>>([
    undefined,
  ]);
  const [loadError, setLoadError] = useState('');
  const [selectedEvent, setSelectedEvent] = useState<AuditEvent>();
  const requestSequence = useRef(0);

  const load = async (
    cursor: number | undefined,
    nextStack: Array<number | undefined>,
  ) => {
    const sequence = ++requestSequence.current;
    setLoading(true);
    setLoadError('');
    try {
      const values = await getAuditEvents(REQUEST_LIMIT, cursor);
      if (sequence !== requestSequence.current) return;
      setRows(values.slice(0, PAGE_SIZE));
      setHasMore(values.length > PAGE_SIZE);
      setCursorStack(nextStack);
      setHasLoaded(true);
    } catch (error) {
      if (sequence !== requestSequence.current) return;
      const failure = resolveFailure(error);
      if (failure.accessLost) {
        setRows([]);
        setHasMore(false);
        setSelectedEvent(undefined);
      }
      setLoadError(failure.message);
      setHasLoaded(true);
    } finally {
      if (sequence === requestSequence.current) setLoading(false);
    }
  };

  useEffect(() => {
    if (actor.systemAdmin) void load(undefined, [undefined]);
    return () => {
      requestSequence.current += 1;
    };
  }, [actor.systemAdmin]);

  if (!actor.systemAdmin) {
    return (
      <AdminPage title="安全审计" description="此页面仅供系统管理员使用。">
        <Result status="403" title="无权访问" />
      </AdminPage>
    );
  }

  const currentCursor = cursorStack[cursorStack.length - 1];
  const previousPage = () => {
    if (cursorStack.length <= 1) return;
    const nextStack = cursorStack.slice(0, -1);
    void load(nextStack[nextStack.length - 1], nextStack);
  };
  const nextPage = () => {
    if (!hasMore || !rows.length) return;
    const cursor = rows[rows.length - 1].id;
    void load(cursor, [...cursorStack, cursor]);
  };
  const refreshCurrent = () => void load(currentCursor, cursorStack);
  const viewLatest = () => void load(undefined, [undefined]);

  const columns: ColumnsType<AuditEvent> = [
    {
      title: <Tooltip title={ADMIN_TIME_ZONE_LABEL}>时间（北京时间）</Tooltip>,
      dataIndex: 'createdAt',
      width: 190,
      render: (value: string | null) => formatAdminDateTime(value),
    },
    {
      title: '事件',
      dataIndex: 'eventType',
      width: 240,
      render: (value: string, event) => {
        const presentation = presentAuditEvent(event);
        return (
          <Space direction="vertical" size={2}>
            <Typography.Text>{presentation.label}</Typography.Text>
            {presentation.label === '其他事件' ? (
              <Typography.Text code type="secondary">
                {value}
              </Typography.Text>
            ) : null}
          </Space>
        );
      },
    },
    {
      title: '操作身份',
      dataIndex: 'actorUserId',
      width: 190,
      render: (value: string | null) => presentAuditActor(value),
    },
    {
      title: '对象',
      key: 'object',
      width: 260,
      render: (_, event) => (
        <Typography.Text ellipsis>
          {presentAuditEvent(event).object}
        </Typography.Text>
      ),
    },
    {
      title: '结果',
      key: 'result',
      width: 120,
      render: (_, event) => {
        const result = presentAuditEvent(event).result;
        const color =
          result === '成功' || result === '已接受'
            ? 'green'
            : result === '失败'
            ? 'red'
            : result === '未标注'
            ? 'default'
            : 'gold';
        return <Tag color={color}>{result}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 110,
      render: (_, event) => (
        <Button type="link" onClick={() => setSelectedEvent(event)}>
          查看详情
        </Button>
      ),
    },
  ];

  const showTable = loading || rows.length > 0 || !loadError;
  const detailPresentation = selectedEvent
    ? presentAuditEvent(selectedEvent)
    : undefined;
  const details = selectedEvent?.details || {};
  const requestId = stringDetail(details, 'requestId');
  const endpoint = [
    stringDetail(details, 'method'),
    stringDetail(details, 'path'),
  ]
    .filter(Boolean)
    .join(' ');
  const status =
    details.status === undefined || details.status === null
      ? undefined
      : String(details.status);

  return (
    <AdminPage
      title="安全审计"
      description="只读查看安全和管理事件。按事件 ID 使用游标浏览，不展示虚构页码或总记录数。"
      actions={
        <Space wrap>
          <Button disabled={loading} onClick={refreshCurrent}>
            刷新当前记录
          </Button>
          <Button type="primary" disabled={loading} onClick={viewLatest}>
            查看最新
          </Button>
        </Space>
      }
    >
      {loadError ? (
        <Alert
          style={{ marginBottom: 16 }}
          type="error"
          showIcon
          message={
            rows.length
              ? '审计记录未更新，当前仍显示上次读取的数据。'
              : '审计记录读取失败'
          }
          description={loadError}
          action={
            <Button disabled={loading} onClick={refreshCurrent}>
              重试
            </Button>
          }
        />
      ) : null}
      {showTable ? (
        <Table<AuditEvent>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          locale={{ emptyText: <Empty description="暂无安全审计记录" /> }}
          pagination={false}
          scroll={{ x: 1120 }}
        />
      ) : null}
      {hasLoaded && !loadError && rows.length > 0 ? (
        <Space
          className="audit-cursor-controls"
          style={{ width: '100%', marginTop: 16, justifyContent: 'flex-end' }}
        >
          <Button
            disabled={cursorStack.length <= 1 || loading}
            onClick={previousPage}
          >
            较新记录
          </Button>
          <Button
            type="primary"
            disabled={!hasMore || loading}
            onClick={nextPage}
          >
            更早记录
          </Button>
        </Space>
      ) : null}

      <Drawer
        title={detailPresentation?.label || '审计详情'}
        open={Boolean(selectedEvent)}
        placement="right"
        width="min(640px, 100vw)"
        onClose={() => setSelectedEvent(undefined)}
        destroyOnClose
      >
        {selectedEvent ? (
          <>
            {detailPresentation?.label === '其他事件' ? (
              <Alert
                style={{ marginBottom: 16 }}
                type="info"
                showIcon
                message={`未登记的事件类型：${selectedEvent.eventType}`}
              />
            ) : null}
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="发生时间（北京时间）">
                {formatAdminDateTime(selectedEvent.createdAt)}
              </Descriptions.Item>
              <Descriptions.Item label="事件类型">
                <Typography.Text code>
                  {selectedEvent.eventType}
                </Typography.Text>
              </Descriptions.Item>
              <Descriptions.Item label="操作身份">
                {selectedEvent.actorUserId || '未记录身份'}
              </Descriptions.Item>
              <Descriptions.Item label="对象摘要">
                {detailPresentation?.object || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="结果">
                {detailPresentation?.result || '未标注'}
              </Descriptions.Item>
              <Descriptions.Item label="事件编号">
                {selectedEvent.id}
              </Descriptions.Item>
              {requestId ? (
                <Descriptions.Item label="Request ID">
                  <Typography.Text code>{requestId}</Typography.Text>
                </Descriptions.Item>
              ) : null}
              {endpoint ? (
                <Descriptions.Item label="请求位置">
                  {endpoint}
                </Descriptions.Item>
              ) : null}
              {status ? (
                <Descriptions.Item label="HTTP 状态">
                  {status}
                </Descriptions.Item>
              ) : null}
              {stringDetail(details, 'peer') ? (
                <Descriptions.Item label="来源地址">
                  {stringDetail(details, 'peer')}
                </Descriptions.Item>
              ) : null}
            </Descriptions>
            <Collapse
              style={{ marginTop: 16 }}
              items={[
                {
                  key: 'technical-details',
                  label: '技术详情（JSON）',
                  children: (
                    <pre
                      style={{
                        margin: 0,
                        maxHeight: '45vh',
                        overflow: 'auto',
                        whiteSpace: 'pre-wrap',
                        overflowWrap: 'anywhere',
                      }}
                    >
                      {JSON.stringify(details, null, 2)}
                    </pre>
                  ),
                },
              ]}
            />
          </>
        ) : null}
      </Drawer>
    </AdminPage>
  );
};

export default AdminAuditEventsPage;
