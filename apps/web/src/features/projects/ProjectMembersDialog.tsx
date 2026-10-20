import { useCurrentUser } from '@/features/auth/context';
import {
  getProjectMembers,
  inviteProjectUser,
  leaveProject,
  removeProjectMember,
  searchProjectUsers,
  transferProjectOwner,
  updateProjectMember,
  type ProjectAccess,
  type ProjectMember,
  type ProjectRole,
  type ProjectUserOption,
} from './service';
import { DeleteOutlined, CrownOutlined } from '@ant-design/icons';
import { Alert, App, Button, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'umi';

type Props = { projectId: string; access: ProjectAccess; open: boolean; onClose: () => void };

const roleLabel: Record<ProjectRole, string> = { OWNER: '所有者', ADMIN: '管理员', EDITOR: '编辑者', VIEWER: '只读成员' };

const ProjectMembersDialog: React.FC<Props> = ({ projectId, access, open, onClose }) => {
  const currentUser = useCurrentUser();
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
  const [members, setMembers] = useState<ProjectMember[]>([]);
  const [memberTotal, setMemberTotal] = useState(0);
  const [memberPage, setMemberPage] = useState(1);
  const [memberPageSize, setMemberPageSize] = useState(20);
  const [loadError, setLoadError] = useState('');
  const [loading, setLoading] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [users, setUsers] = useState<ProjectUserOption[]>([]);
  const [searching, setSearching] = useState(false);
  const [selectedUser, setSelectedUser] = useState<string>();
  const [inviteRole, setInviteRole] = useState<ProjectRole>('EDITOR');
  const [submitting, setSubmitting] = useState(false);

  const capabilities = access.capabilities || [];
  const isOwner = access.role === 'OWNER';
  const isSystemAdmin = access.role === 'SYSTEM_ADMIN';
  const canManage = capabilities.includes('MANAGE_MEMBERS');
  const canTransferOwnership = capabilities.includes('TRANSFER_OWNERSHIP');
  const inviteRoles = useMemo(() => (isOwner || isSystemAdmin
    ? ['ADMIN', 'EDITOR', 'VIEWER'] as ProjectRole[]
    : ['EDITOR', 'VIEWER'] as ProjectRole[]), [isOwner, isSystemAdmin]);

  const refresh = async () => {
    setLoading(true);
    try {
      setLoadError('');
      const response = await getProjectMembers(projectId, memberPage, memberPageSize);
      setMembers(response.data || []);
      setMemberTotal(response.addition?.total || 0);
    } catch (error) {
      setLoadError((error as Error)?.message || '成员列表加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { if (open) void refresh(); }, [open, projectId, memberPage, memberPageSize]);

  const searchUsers = async () => {
    if (searchText.trim().length < 2) {
      message.warning('请输入至少两个字符搜索已注册用户');
      return;
    }
    setSearching(true);
    try {
      const response = await searchProjectUsers(searchText.trim(), 1, 20);
      setUsers(response.data || []);
    } catch {
      setUsers([]);
    } finally {
      setSearching(false);
    }
  };

  const submitInvitation = async () => {
    if (!selectedUser) {
      message.warning('先搜索并选择一个已启用的内部用户');
      return;
    }
    setSubmitting(true);
    try {
      await inviteProjectUser(projectId, selectedUser, inviteRole as Exclude<ProjectRole, 'OWNER'>);
      message.success('邀请已发送，等待对方接受');
      setSelectedUser(undefined);
      setUsers([]);
      setSearchText('');
      await refresh();
    } catch (error) {
      message.error((error as Error)?.message || '发送邀请失败');
    } finally {
      setSubmitting(false);
    }
  };

  const canManageMember = (member: ProjectMember) => {
    if (!canManage || member.role === 'OWNER' || member.userId === currentUser.id) return false;
    if (isSystemAdmin || isOwner) return true;
    return access.role === 'ADMIN' && (member.role === 'EDITOR' || member.role === 'VIEWER');
  };

  const changeMemberRole = async (member: ProjectMember, role: ProjectRole) => {
    try {
      await updateProjectMember(projectId, member.userId, role as Exclude<ProjectRole, 'OWNER'>);
      message.success('成员角色已更新');
      await refresh();
    } catch (error) {
      message.error((error as Error)?.message || '角色更新失败');
    }
  };

  const deleteMember = async (member: ProjectMember) => {
    try {
      await removeProjectMember(projectId, member.userId);
      message.success('成员已移除');
      await refresh();
    } catch (error) {
      message.error((error as Error)?.message || '移除成员失败');
    }
  };

  const transferOwnership = async (member: ProjectMember) => {
    try {
      await transferProjectOwner(projectId, member.userId);
      message.success(`所有权已转移给 ${member.displayName || member.username}`);
      onClose();
    } catch (error) {
      message.error((error as Error)?.message || '所有权转移失败');
    }
  };

  const handleLeave = () => {
    modal.confirm({
      title: '退出项目',
      content: '退出后将无法再访问该项目及其图谱。',
      okText: '退出项目',
      cancelText: '取消',
      onOk: async () => {
        await leaveProject(projectId);
        message.success('已退出项目');
        onClose();
        navigate('/home');
      },
    });
  };

  const columns: ColumnsType<ProjectMember> = [
    {
      title: '成员',
      key: 'member',
      render: (_, member) => <Space direction="vertical" size={0}>
        <Typography.Text ellipsis>{member.displayName || member.username}</Typography.Text>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>{member.username}</Typography.Text>
      </Space>,
    },
    {
      title: '角色',
      dataIndex: 'role',
      key: 'role',
      width: 130,
      render: (role: ProjectRole, member) => canManageMember(member)
        ? <Select aria-label={`${member.username}角色`} size="small" value={role}
            options={(isOwner || isSystemAdmin ? ['ADMIN', 'EDITOR', 'VIEWER'] : ['EDITOR', 'VIEWER'])
              .map((value) => ({ value, label: roleLabel[value as ProjectRole] }))}
            onChange={(value) => void changeMemberRole(member, value as ProjectRole)} />
        : <Tag color={role === 'OWNER' ? 'gold' : role === 'ADMIN' ? 'blue' : 'default'}>
            {roleLabel[role]}{member.userId === currentUser.id ? ' · 你' : ''}
          </Tag>,
    },
    {
      title: '操作', key: 'actions', width: 110,
      render: (_, member) => (
        <Space>
          {canTransferOwnership && member.role !== 'OWNER' ? (
            <Popconfirm title="将项目所有权转移给该成员？你将变为管理员。" onConfirm={() => transferOwnership(member)}>
              <Button type="link" size="small" icon={<CrownOutlined />}>转移</Button>
            </Popconfirm>
          ) : null}
          {canManageMember(member) ? (
            <Popconfirm title="确定移除该成员？" onConfirm={() => deleteMember(member)}>
              <Button type="link" danger size="small" icon={<DeleteOutlined />} aria-label={`移除${member.username}`} />
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Modal title="项目成员" open={open} onCancel={onClose} footer={null} width={720} destroyOnClose>
      {loadError ? <Alert type="error" showIcon message="成员列表加载失败" description={loadError}
        action={<Button onClick={() => void refresh()}>重试</Button>} /> : null}
      <div style={{ maxHeight: 'min(60vh, 520px)', overflowY: 'auto', minWidth: 0 }}>
        <Table<ProjectMember> size="small" rowKey="userId" loading={loading} columns={columns}
          dataSource={loadError ? [] : members} scroll={{ x: 520 }} pagination={{ current: memberPage, pageSize: memberPageSize,
            total: memberTotal, showSizeChanger: true, onChange: (nextPage, nextSize) => { setMemberPage(nextPage); setMemberPageSize(nextSize); } }} />
      </div>

      {canManage ? <section style={{ marginTop: 24 }} aria-label="邀请成员">
        <Typography.Title level={5}>邀请已注册用户</Typography.Title>
        <Space.Compact style={{ width: '100%' }}>
          <Input.Search placeholder="输入用户名或显示名称，至少两个字符" value={searchText}
            onChange={(event) => setSearchText(event.target.value)} onSearch={() => void searchUsers()}
            loading={searching} />
        </Space.Compact>
        <Space.Compact style={{ width: '100%', marginTop: 8 }}>
          <Select showSearch filterOption={false} placeholder="选择搜索结果" value={selectedUser}
            onChange={setSelectedUser} style={{ flex: 1 }} options={users.map((user) => ({
              value: user.id, label: `${user.displayName || user.username}（${user.username}）`,
            }))} notFoundContent={searching ? '搜索中…' : '暂无可选用户'} />
          <Select value={inviteRole} onChange={setInviteRole}
            options={inviteRoles.map((role) => ({ value: role, label: roleLabel[role] }))} />
          <Button type="primary" loading={submitting} onClick={() => void submitInvitation()}>发送邀请</Button>
        </Space.Compact>
      </section> : null}

      {!isOwner ? <div style={{ marginTop: 20, textAlign: 'right' }}>
        <Button danger type="link" onClick={handleLeave}>退出项目</Button>
      </div> : null}
    </Modal>
  );
};

export default ProjectMembersDialog;
