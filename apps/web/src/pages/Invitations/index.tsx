import { formatAdminDateTime } from '@/features/admin/dateTime';
import { normalizeApiError } from '@/features/errors/apiError';
import PageShell from '@/features/layout/PageShell';
import ListPagination from '@/features/lists/ListPagination';
import {
  getMyInvitations,
  respondProjectInvitation,
  type ProjectInvitation,
} from '@/features/projects/service';
import {
  CalendarOutlined,
  CheckOutlined,
  CloseOutlined,
  MailOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { Alert, App, Button, Empty, List, Space, Tag, Typography } from 'antd';
import React, { useCallback, useEffect, useRef, useState } from 'react';

const roleName: Record<string, string> = {
  ADMIN: '管理员',
  EDITOR: '编辑者',
  VIEWER: '只读成员',
};

const InvitationsPage: React.FC = () => {
  const { message } = App.useApp();
  const [rows, setRows] = useState<ProjectInvitation[]>([]);
  const [loading, setLoading] = useState(true);
  const [hasLoaded, setHasLoaded] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [busyId, setBusyId] = useState('');
  const [uncertainInvitationId, setUncertainInvitationId] = useState('');
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const requestSequence = useRef(0);

  const refresh = useCallback(
    async (nextPage = page, nextSize = size) => {
      const sequence = ++requestSequence.current;
      setLoading(true);
      setLoadError('');
      try {
        const response = await getMyInvitations(nextPage, nextSize);
        if (sequence !== requestSequence.current) return;
        setRows(response.data || []);
        setTotal(response.addition?.total || 0);
        setPage(nextPage);
        setSize(nextSize);
        setHasLoaded(true);
        // 只有成功读取服务端当前状态后，才解除不确定写入的保护。
        setUncertainInvitationId('');
      } catch (error) {
        if (sequence !== requestSequence.current) return;
        // 查询失败与空邀请是不同状态，保留可重试的错误反馈。
        setLoadError((error as Error)?.message || '邀请加载失败');
        setHasLoaded(true);
      } finally {
        if (sequence === requestSequence.current) setLoading(false);
      }
    },
    [page, size],
  );

  useEffect(() => {
    void refresh();
    return () => {
      requestSequence.current += 1;
    };
  }, [refresh]);

  const respond = async (
    invitation: ProjectInvitation,
    action: 'accept' | 'decline',
  ) => {
    setBusyId(invitation.id);
    try {
      const response = await respondProjectInvitation(invitation.id, action);
      const status = response.data?.status;
      if (status === 'ACCEPTED')
        message.success(`已加入项目“${invitation.projectName}”`);
      else if (status === 'DECLINED') message.success('已拒绝邀请');
      else if (status === 'REVOKED')
        message.warning('邀请已失效，项目成员权限或状态已变更');
      else if (status === 'EXPIRED') message.warning('邀请已过期');
      await refresh(page, size);
    } catch (error) {
      const failure = normalizeApiError(error);
      if (failure.writeOutcomeUnknown) {
        setUncertainInvitationId(invitation.id);
        message.warning(
          '邀请处理结果未确认，正在读取最新状态；系统不会自动重复提交。',
        );
        await refresh(page, size);
      } else {
        message.error(failure.message || '处理邀请失败');
      }
    } finally {
      setBusyId('');
    }
  };

  return (
    <PageShell
      title="站内邀请"
      titleIcon={<MailOutlined aria-hidden="true" />}
      actions={
        <Button
          icon={<ReloadOutlined aria-hidden="true" />}
          disabled={loading}
          onClick={() => void refresh()}
        >
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
            rows.length
              ? '邀请列表未更新，仍显示上次读取的数据。'
              : '邀请列表读取失败'
          }
          description={loadError}
          action={
            <Button disabled={loading} onClick={() => void refresh()}>
              重试
            </Button>
          }
        />
      ) : null}
      {uncertainInvitationId ? (
        <Alert
          style={{ marginBottom: 16 }}
          type="warning"
          showIcon
          message="操作结果仍未确认，相关邀请暂不可再次处理。"
          description="请重新读取邀请列表；读取成功后，页面会按服务端状态恢复可用操作。"
          action={
            <Button disabled={loading} onClick={() => void refresh()}>
              重新读取
            </Button>
          }
        />
      ) : null}
      <List<ProjectInvitation>
        loading={loading}
        dataSource={rows}
        locale={{
          emptyText: loadError ? (
            '列表读取失败'
          ) : (
            <Empty description="目前没有待处理的项目邀请" />
          ),
        }}
        renderItem={(item) => (
          <List.Item
            key={item.id}
            actions={[
              <Button
                key="accept"
                type="primary"
                icon={<CheckOutlined />}
                loading={busyId === item.id}
                disabled={
                  busyId === item.id ||
                  Boolean(loadError) ||
                  uncertainInvitationId === item.id
                }
                onClick={() => void respond(item, 'accept')}
              >
                接受
              </Button>,
              <Button
                key="decline"
                icon={<CloseOutlined />}
                disabled={
                  busyId === item.id ||
                  Boolean(loadError) ||
                  uncertainInvitationId === item.id
                }
                onClick={() => void respond(item, 'decline')}
              >
                拒绝
              </Button>,
            ]}
          >
            <List.Item.Meta
              title={
                <Space wrap>
                  <Typography.Text strong>{item.projectName}</Typography.Text>
                  <Tag color="blue">{roleName[item.role] || item.role}</Tag>
                </Space>
              }
              description={
                <Space wrap>
                  <span>
                    {item.inviterDisplayName ||
                      (item.inviterUsername
                        ? `@${item.inviterUsername}`
                        : '邀请人账号不可用')}{' '}
                    邀请你加入
                  </span>
                  <span>
                    <CalendarOutlined /> 有效至{' '}
                    {formatAdminDateTime(item.expiresTime)}
                  </span>
                </Space>
              }
            />
          </List.Item>
        )}
      />
      {hasLoaded && !loadError ? (
        <ListPagination
          current={page}
          pageSize={size}
          total={total}
          loading={loading}
          onChange={(nextPage, nextSize) => void refresh(nextPage, nextSize)}
        />
      ) : null}
    </PageShell>
  );
};

export default InvitationsPage;
