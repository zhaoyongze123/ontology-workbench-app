import { getMyInvitations, respondProjectInvitation, type ProjectInvitation } from '@/features/projects/service';
import { CalendarOutlined, CheckOutlined, CloseOutlined, MailOutlined } from '@ant-design/icons';
import { Alert, App, Button, Card, Empty, List, Space, Tag, Typography } from 'antd';
import React, { useCallback, useEffect, useState } from 'react';

const roleName: Record<string, string> = { ADMIN: '管理员', EDITOR: '编辑者', VIEWER: '只读成员' };

const InvitationsPage: React.FC = () => {
  const { message } = App.useApp();
  const [rows, setRows] = useState<ProjectInvitation[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [busyId, setBusyId] = useState('');

  const refresh = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const response = await getMyInvitations(1, 100);
      setRows(response.data || []);
    } catch (error) {
      // 查询失败与空邀请是不同状态，保留可重试的错误反馈。
      setLoadError((error as Error)?.message || '邀请加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);

  const respond = async (invitation: ProjectInvitation, action: 'accept' | 'decline') => {
    setBusyId(invitation.id);
    try {
      const response = await respondProjectInvitation(invitation.id, action);
      const status = response.data?.status;
      if (status === 'ACCEPTED') message.success(`已加入项目“${invitation.projectName}”`);
      else if (status === 'DECLINED') message.success('已拒绝邀请');
      else if (status === 'REVOKED') message.warning('邀请已失效，项目成员权限或状态已变更');
      else if (status === 'EXPIRED') message.warning('邀请已过期');
      await refresh();
    } catch (error) {
      message.error((error as Error)?.message || '处理邀请失败');
    } finally {
      setBusyId('');
    }
  };

  return (
    <main style={{ maxWidth: 960, margin: '0 auto', padding: '28px 24px' }}>
      <Space align="center" style={{ marginBottom: 20 }}>
        <MailOutlined style={{ fontSize: 20, color: '#570cf4' }} />
        <Typography.Title level={3} style={{ margin: 0 }}>站内邀请</Typography.Title>
      </Space>
      <Card bordered={false}>
        {loadError ? <Alert type="error" showIcon message={loadError}
          action={<Button onClick={() => void refresh()}>重试</Button>} /> : null}
        <List<ProjectInvitation>
          loading={loading}
          dataSource={loadError ? [] : rows}
          locale={{ emptyText: loadError ? null : <Empty description="目前没有待处理的项目邀请" /> }}
          renderItem={(item) => (
            <List.Item
              key={item.id}
              actions={[
                <Button key="accept" type="primary" icon={<CheckOutlined />} loading={busyId === item.id}
                  onClick={() => void respond(item, 'accept')}>接受</Button>,
                <Button key="decline" icon={<CloseOutlined />} disabled={busyId === item.id}
                  onClick={() => void respond(item, 'decline')}>拒绝</Button>,
              ]}
            >
              <List.Item.Meta
                title={<Space wrap><Typography.Text strong>{item.projectName}</Typography.Text>
                  <Tag color="blue">{roleName[item.role] || item.role}</Tag></Space>}
                description={<Space wrap>
                  <span>{item.inviterDisplayName || item.inviterUserId} 邀请你加入</span>
                  <span><CalendarOutlined /> 有效至 {new Date(item.expiresTime).toLocaleString()}</span>
                </Space>}
              />
            </List.Item>
          )}
        />
      </Card>
    </main>
  );
};

export default InvitationsPage;
