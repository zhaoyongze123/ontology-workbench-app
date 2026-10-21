import { formatAdminDateTime } from '@/features/admin/dateTime';
import { oauthScopeLabel } from '@/features/admin/oauthScopePresentation';
import {
  getAppAuthorizations,
  revokeAppAuthorization,
  type AppAuthorization,
} from '@/features/admin/service';
import PageShell from '@/features/layout/PageShell';
import ListPagination from '@/features/lists/ListPagination';
import {
  Alert,
  App,
  Button,
  Empty,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React, { useEffect, useMemo, useRef, useState } from 'react';

const AppAuthorizationsPage: React.FC = () => {
  const { message } = App.useApp();
  const [items, setItems] = useState<AppAuthorization[]>([]);
  const [loading, setLoading] = useState(false);
  const [hasLoaded, setHasLoaded] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [revokingId, setRevokingId] = useState<string>();
  const requestSequence = useRef(0);

  const load = async () => {
    const sequence = ++requestSequence.current;
    setLoading(true);
    setLoadError('');
    try {
      const result = await getAppAuthorizations();
      if (sequence !== requestSequence.current) return;
      setItems(result);
      setHasLoaded(true);
    } catch (error) {
      if (sequence !== requestSequence.current) return;
      setLoadError((error as Error)?.message || '授权列表读取失败');
      setHasLoaded(true);
    } finally {
      if (sequence === requestSequence.current) setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    return () => {
      requestSequence.current += 1;
    };
  }, []);

  const sortedItems = useMemo(
    () =>
      [...items].sort((left, right) => {
        const timeOrder =
          Date.parse(right.authorizedAt) - Date.parse(left.authorizedAt);
        return Number.isFinite(timeOrder) && timeOrder !== 0
          ? timeOrder
          : right.id.localeCompare(left.id);
      }),
    [items],
  );
  const pageRows = sortedItems.slice((page - 1) * pageSize, page * pageSize);

  useEffect(() => {
    const lastPage = Math.max(1, Math.ceil(items.length / pageSize));
    if (page > lastPage) setPage(lastPage);
  }, [items.length, page, pageSize]);

  const columns: ColumnsType<AppAuthorization> = [
    {
      title: '应用',
      dataIndex: 'applicationName',
      render: (value: string) => (
        <Typography.Text strong ellipsis>
          {value}
        </Typography.Text>
      ),
    },
    {
      title: '权限范围',
      dataIndex: 'scopes',
      render: (value: string) => (
        <Space wrap>
          {(value || '')
            .split(/\s+/)
            .filter(Boolean)
            .map((scope) => (
              <Tag key={scope}>{oauthScopeLabel(scope)}</Tag>
            ))}
        </Space>
      ),
    },
    {
      title: '授权时间',
      dataIndex: 'authorizedAt',
      width: 190,
      render: (value: string) => formatAdminDateTime(value),
    },
    {
      title: '有效至',
      dataIndex: 'absoluteExpiresAt',
      width: 190,
      render: (value: string) => formatAdminDateTime(value),
    },
    {
      title: '状态',
      dataIndex: 'revokedAt',
      width: 100,
      render: (value: string | null, item) =>
        value ? (
          <Tag>已撤销</Tag>
        ) : Date.parse(item.absoluteExpiresAt) <= Date.now() ? (
          <Tag>已到期</Tag>
        ) : (
          <Tag color="green">有效</Tag>
        ),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, item) =>
        !item.revokedAt && Date.parse(item.absoluteExpiresAt) > Date.now() ? (
          <Popconfirm
            title="撤销此应用授权？"
            description="该连接的访问和刷新令牌将失效。"
            onConfirm={async () => {
              setRevokingId(item.id);
              try {
                await revokeAppAuthorization(item.id);
                message.success('授权已撤销');
                await load();
              } catch (error) {
                message.error((error as Error)?.message || '撤销失败');
              } finally {
                setRevokingId(undefined);
              }
            }}
          >
            <Button danger type="link" loading={revokingId === item.id}>
              撤销
            </Button>
          </Popconfirm>
        ) : null,
    },
  ];

  return (
    <PageShell
      title="我的 App 授权"
      actions={
        <Button disabled={loading} onClick={() => void load()}>
          刷新
        </Button>
      }
    >
      {loadError ? (
        <Alert
          style={{ marginBottom: 16 }}
          type="error"
          showIcon
          message={
            items.length
              ? '授权列表未更新，仍显示上次读取的数据。'
              : '授权列表读取失败'
          }
          description={loadError}
          action={
            <Button disabled={loading} onClick={() => void load()}>
              重试
            </Button>
          }
        />
      ) : null}
      {hasLoaded || loading ? (
        <Table<AppAuthorization>
          rowKey="id"
          columns={columns}
          dataSource={pageRows}
          loading={loading}
          scroll={{ x: 1000 }}
          pagination={false}
          locale={{
            emptyText: loadError ? (
              '列表读取失败'
            ) : (
              <Empty description="当前没有 App 授权记录" />
            ),
          }}
        />
      ) : null}
      {hasLoaded && !loadError ? (
        <ListPagination
          current={page}
          pageSize={pageSize}
          total={items.length}
          loading={loading}
          onChange={(nextPage, nextSize) => {
            setPage(nextPage);
            setPageSize(nextSize);
          }}
        />
      ) : null}
    </PageShell>
  );
};

export default AppAuthorizationsPage;
