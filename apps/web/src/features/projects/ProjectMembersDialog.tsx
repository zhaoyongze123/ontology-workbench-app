import { useCurrentUser } from '@/features/auth/context';
import { normalizeApiError } from '@/features/errors/apiError';
import ListPagination from '@/features/lists/ListPagination';
import { CrownOutlined, DeleteOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  getProjectInviteCandidates,
  getProjectMembers,
  inviteProjectUser,
  removeProjectMember,
  transferProjectOwner,
  updateProjectMember,
  type ProjectAccess,
  type ProjectMember,
  type ProjectRole,
  type ProjectUserOption,
} from './service';

type Props = {
  projectId: string;
  access: ProjectAccess;
  open: boolean;
  onClose: () => void;
};

const roleLabel: Record<ProjectRole, string> = {
  OWNER: '所有者',
  ADMIN: '管理员',
  EDITOR: '编辑者',
  VIEWER: '只读成员',
};

const ProjectMembersDialog: React.FC<Props> = ({
  projectId,
  access,
  open,
  onClose,
}) => {
  const currentUser = useCurrentUser();
  const { message } = App.useApp();
  const [members, setMembers] = useState<ProjectMember[]>([]);
  const [memberTotal, setMemberTotal] = useState(0);
  const [memberPage, setMemberPage] = useState(1);
  const [memberPageSize, setMemberPageSize] = useState(20);
  const [loadError, setLoadError] = useState('');
  const [loading, setLoading] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [users, setUsers] = useState<ProjectUserOption[]>([]);
  const [candidateTotal, setCandidateTotal] = useState(0);
  const [candidatePage, setCandidatePage] = useState(1);
  const [candidateLoadError, setCandidateLoadError] = useState('');
  const [candidateRefresh, setCandidateRefresh] = useState(0);
  const [unknownInviteUserId, setUnknownInviteUserId] = useState<string>();
  const [searching, setSearching] = useState(false);
  const [selectedUser, setSelectedUser] = useState<ProjectUserOption>();
  const [inviteRole, setInviteRole] = useState<ProjectRole>('EDITOR');
  const [submitting, setSubmitting] = useState(false);
  const [changingRoleId, setChangingRoleId] = useState<string>();
  const [removingMemberId, setRemovingMemberId] = useState<string>();
  const [transferringMemberId, setTransferringMemberId] = useState<string>();
  const requestSequence = useRef(0);
  const candidateSequence = useRef(0);
  const candidateLoading = useRef(false);

  const capabilities = access.capabilities || [];
  const isOwner = access.role === 'OWNER';
  const isSystemAdmin = access.role === 'SYSTEM_ADMIN';
  const canManage = capabilities.includes('MANAGE_MEMBERS');
  const canTransferOwnership = capabilities.includes('TRANSFER_OWNERSHIP');
  const inviteRoles = useMemo(
    () =>
      isOwner || isSystemAdmin
        ? (['ADMIN', 'EDITOR', 'VIEWER'] as ProjectRole[])
        : (['EDITOR', 'VIEWER'] as ProjectRole[]),
    [isOwner, isSystemAdmin],
  );
  const inviteOptions =
    selectedUser && !users.some((user) => user.id === selectedUser.id)
      ? [selectedUser, ...users]
      : users;

  const refresh = async () => {
    const sequence = ++requestSequence.current;
    setLoading(true);
    try {
      setLoadError('');
      const response = await getProjectMembers(
        projectId,
        memberPage,
        memberPageSize,
      );
      if (sequence !== requestSequence.current) return;
      setMembers(response.data || []);
      setMemberTotal(response.addition?.total || 0);
    } catch (error) {
      if (sequence !== requestSequence.current) return;
      setLoadError((error as Error)?.message || '成员列表加载失败');
    } finally {
      if (sequence === requestSequence.current) setLoading(false);
    }
  };

  useEffect(() => {
    if (open) void refresh();
    return () => {
      requestSequence.current += 1;
    };
  }, [open, projectId, memberPage, memberPageSize]);

  const loadInviteCandidates = async (
    keyword: string,
    page: number,
    append: boolean,
  ) => {
    if (candidateLoading.current) return;
    const sequence = ++candidateSequence.current;
    candidateLoading.current = true;
    setSearching(true);
    try {
      const response = await getProjectInviteCandidates(
        projectId,
        keyword,
        page,
        20,
      );
      if (sequence !== candidateSequence.current) return;
      const nextUsers = response.data || [];
      setUsers((current) =>
        append
          ? [
              ...current,
              ...nextUsers.filter(
                (user) => !current.some((item) => item.id === user.id),
              ),
            ]
          : nextUsers,
      );
      const refreshedSelection = nextUsers.find(
        (user) => user.id === selectedUser?.id,
      );
      if (refreshedSelection) {
        setSelectedUser(refreshedSelection);
        if (refreshedSelection.id === unknownInviteUserId) {
          setUnknownInviteUserId(undefined);
        }
      } else if (
        unknownInviteUserId &&
        selectedUser?.id === unknownInviteUserId
      ) {
        setSelectedUser(undefined);
        setUnknownInviteUserId(undefined);
        message.info(
          '该用户目前不在可邀请列表中，请重新选择；可能已加入项目或账号状态已变化。',
        );
      }
      setCandidateTotal(response.addition?.total || 0);
      setCandidatePage(page);
      setCandidateLoadError('');
    } catch (error) {
      if (sequence !== candidateSequence.current) return;
      setCandidateLoadError((error as Error)?.message || '用户列表加载失败');
      if (!append) {
        setUsers([]);
        setCandidateTotal(0);
      }
    } finally {
      if (sequence === candidateSequence.current) {
        candidateLoading.current = false;
        setSearching(false);
      }
    }
  };

  useEffect(() => {
    if (!open || !canManage) return;
    // 默认打开时直接列出第一页可邀请账号；搜索只改变服务端筛选词。
    const timer = window.setTimeout(
      () => {
        candidateLoading.current = false;
        void loadInviteCandidates(searchText, 1, false);
      },
      searchText ? 250 : 0,
    );
    return () => {
      window.clearTimeout(timer);
      candidateSequence.current += 1;
      candidateLoading.current = false;
    };
  }, [open, canManage, projectId, searchText, candidateRefresh]);

  const submitInvitation = async () => {
    if (!selectedUser || selectedUser.invitationPending || submitting) return;
    setSubmitting(true);
    try {
      await inviteProjectUser(
        projectId,
        selectedUser.id,
        inviteRole as Exclude<ProjectRole, 'OWNER'>,
      );
      message.success('邀请已发送，等待对方接受');
      setSelectedUser(undefined);
      setUsers([]);
      setSearchText('');
      setCandidateTotal(0);
      // 邀请状态由服务端更新；重新取候选列表后立即显示“已邀请”。
      setCandidateRefresh((value) => value + 1);
      await refresh();
    } catch (error) {
      const failure = normalizeApiError(error);
      if (failure.writeOutcomeUnknown) {
        message.warning(
          '邀请结果未确认；系统不会重复提交。正在读取该用户的最新状态。',
        );
        // 只重新读取候选状态，不自动重放创建邀请的写请求。
        setUnknownInviteUserId(selectedUser.id);
        setSearchText(selectedUser.username);
        setUsers([]);
        setCandidateTotal(0);
        setCandidateLoadError('');
        setCandidateRefresh((value) => value + 1);
      } else {
        message.error(failure.message || '发送邀请失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const canManageMember = (member: ProjectMember) => {
    if (
      !canManage ||
      member.role === 'OWNER' ||
      member.userId === currentUser.id
    )
      return false;
    if (isSystemAdmin || isOwner) return true;
    return (
      access.role === 'ADMIN' &&
      (member.role === 'EDITOR' || member.role === 'VIEWER')
    );
  };

  const changeMemberRole = async (member: ProjectMember, role: ProjectRole) => {
    setChangingRoleId(member.userId);
    try {
      await updateProjectMember(
        projectId,
        member.userId,
        role as Exclude<ProjectRole, 'OWNER'>,
      );
      message.success('成员角色已更新');
      await refresh();
    } catch (error) {
      const failure = normalizeApiError(error);
      if (failure.writeOutcomeUnknown) {
        message.warning('角色更新结果未确认，正在读取该成员的最新角色。');
        await refresh();
      } else {
        message.error(failure.message || '角色更新失败');
      }
    } finally {
      setChangingRoleId(undefined);
    }
  };

  const deleteMember = async (member: ProjectMember) => {
    setRemovingMemberId(member.userId);
    try {
      await removeProjectMember(projectId, member.userId);
      message.success('成员已移除');
      await refresh();
    } catch (error) {
      const failure = normalizeApiError(error);
      if (failure.writeOutcomeUnknown) {
        message.warning('移除结果未确认，正在核对项目成员列表。');
        await refresh();
      } else {
        message.error(failure.message || '移除成员失败');
      }
    } finally {
      setRemovingMemberId(undefined);
    }
  };

  const transferOwnership = async (member: ProjectMember) => {
    setTransferringMemberId(member.userId);
    try {
      await transferProjectOwner(projectId, member.userId);
      message.success(
        `所有权已转移给 ${member.displayName || member.username}`,
      );
      onClose();
    } catch (error) {
      const failure = normalizeApiError(error);
      if (failure.writeOutcomeUnknown) {
        message.warning(
          '所有权转移结果未确认，请重新打开项目成员查看最新角色。',
        );
        onClose();
      } else {
        message.error(failure.message || '所有权转移失败');
      }
    } finally {
      setTransferringMemberId(undefined);
    }
  };

  const closeDialog = () => {
    candidateSequence.current += 1;
    candidateLoading.current = false;
    setSearchText('');
    setSelectedUser(undefined);
    setCandidateLoadError('');
    onClose();
  };

  const columns: ColumnsType<ProjectMember> = [
    {
      title: '成员',
      key: 'member',
      render: (_, member) => (
        <Space direction="vertical" size={0}>
          <Typography.Text ellipsis>
            {member.displayName || member.username}
          </Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {member.username}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '角色',
      dataIndex: 'role',
      key: 'role',
      width: 130,
      render: (role: ProjectRole, member) =>
        canManageMember(member) ? (
          <Select
            aria-label={`${member.username}角色`}
            size="small"
            value={role}
            loading={changingRoleId === member.userId}
            disabled={Boolean(
              changingRoleId || removingMemberId || transferringMemberId,
            )}
            options={(isOwner || isSystemAdmin
              ? ['ADMIN', 'EDITOR', 'VIEWER']
              : ['EDITOR', 'VIEWER']
            ).map((value) => ({
              value,
              label: roleLabel[value as ProjectRole],
            }))}
            onChange={(value) =>
              void changeMemberRole(member, value as ProjectRole)
            }
          />
        ) : (
          <Tag
            color={
              role === 'OWNER' ? 'gold' : role === 'ADMIN' ? 'blue' : 'default'
            }
          >
            {roleLabel[role]}
            {member.userId === currentUser.id ? ' · 你' : ''}
          </Tag>
        ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 110,
      render: (_, member) => (
        <Space>
          {canTransferOwnership && member.role !== 'OWNER' ? (
            <Popconfirm
              title={`将项目所有权转移给${
                member.displayName || member.username
              }？你将变为管理员。`}
              okButtonProps={{
                loading: transferringMemberId === member.userId,
              }}
              onConfirm={() => transferOwnership(member)}
            >
              <Button
                type="link"
                size="small"
                loading={transferringMemberId === member.userId}
                disabled={Boolean(
                  changingRoleId || removingMemberId || transferringMemberId,
                )}
                icon={<CrownOutlined aria-hidden="true" />}
              >
                转移
              </Button>
            </Popconfirm>
          ) : null}
          {canManageMember(member) ? (
            <Popconfirm
              title={`将${
                member.displayName || member.username
              }移出此项目？其项目访问权限将立即失效。`}
              okButtonProps={{ loading: removingMemberId === member.userId }}
              onConfirm={() => deleteMember(member)}
            >
              <Button
                type="link"
                danger
                size="small"
                loading={removingMemberId === member.userId}
                disabled={Boolean(
                  changingRoleId || removingMemberId || transferringMemberId,
                )}
                icon={<DeleteOutlined aria-hidden="true" />}
                aria-label={`移除${member.username}`}
              />
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Modal
      title="项目成员"
      open={open}
      onCancel={closeDialog}
      footer={null}
      width="min(720px, calc(100vw - 32px))"
      destroyOnClose
    >
      {loadError ? (
        <Alert
          type="error"
          showIcon
          message="成员列表加载失败"
          description={loadError}
          action={<Button onClick={() => void refresh()}>重试</Button>}
        />
      ) : null}
      <div
        style={{
          maxHeight: 'min(60vh, 520px)',
          overflowY: 'auto',
          minWidth: 0,
        }}
      >
        <Table<ProjectMember>
          size="small"
          rowKey="userId"
          loading={loading}
          columns={columns}
          dataSource={loadError ? [] : members}
          scroll={{ x: 520 }}
          pagination={false}
          locale={{ emptyText: loadError ? '列表读取失败' : '暂无项目成员' }}
        />
      </div>
      <ListPagination
        current={memberPage}
        pageSize={memberPageSize}
        total={memberTotal}
        loading={loading || Boolean(loadError)}
        onChange={(nextPage, nextSize) => {
          setMemberPage(nextPage);
          setMemberPageSize(nextSize);
        }}
      />

      {canManage ? (
        <section style={{ marginTop: 24 }} aria-label="邀请成员">
          <Typography.Title level={5}>邀请已注册用户</Typography.Title>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
            可直接选择，也可按姓名或用户名搜索；已是项目成员的账号不会出现在列表中。
          </Typography.Paragraph>
          {candidateLoadError && !unknownInviteUserId ? (
            <Alert
              type="error"
              showIcon
              message="用户列表加载失败"
              description={candidateLoadError}
              action={
                <Button
                  disabled={searching}
                  onClick={() =>
                    void loadInviteCandidates(searchText, 1, false)
                  }
                >
                  重试
                </Button>
              }
              style={{ marginBottom: 8 }}
            />
          ) : null}
          {unknownInviteUserId ? (
            <Alert
              type="warning"
              showIcon
              message="邀请状态尚未确认"
              description={
                candidateLoadError
                  ? `${candidateLoadError} 重新读取成功前，不能再次提交此邀请。`
                  : '重新读取成功前，不能再次提交此邀请。'
              }
              action={
                <Button
                  disabled={searching}
                  onClick={() =>
                    void loadInviteCandidates(searchText, 1, false)
                  }
                >
                  重新读取
                </Button>
              }
              style={{ marginBottom: 8 }}
            />
          ) : null}
          <Select
            aria-label="邀请用户"
            allowClear
            disabled={Boolean(unknownInviteUserId || candidateLoadError)}
            showSearch
            filterOption={false}
            value={selectedUser?.username}
            onChange={(value) =>
              setSelectedUser(
                inviteOptions.find((user) => user.username === value),
              )
            }
            onSearch={(value) => {
              candidateSequence.current += 1;
              candidateLoading.current = false;
              setSearchText(value);
              setUsers([]);
              setCandidateTotal(0);
              setCandidateLoadError('');
            }}
            onPopupScroll={(event) => {
              const element = event.target as HTMLDivElement;
              if (
                element.scrollTop + element.clientHeight >=
                  element.scrollHeight - 24 &&
                users.length < candidateTotal &&
                !searching
              ) {
                void loadInviteCandidates(searchText, candidatePage + 1, true);
              }
            }}
            options={inviteOptions.map((user) => ({
              // Select 的可访问性回退列表会朗读 option value；用用户名避免把内部 ID 暴露给用户。
              value: user.username,
              disabled: user.invitationPending,
              label: (
                <Space size={6}>
                  <span>{user.displayName || user.username}</span>
                  <Typography.Text type="secondary">
                    @{user.username}
                  </Typography.Text>
                  {user.invitationPending ? (
                    <Tag color="gold">已邀请</Tag>
                  ) : null}
                </Space>
              ),
            }))}
            loading={searching}
            placeholder="搜索或选择用户"
            notFoundContent={
              searching
                ? '正在查找用户…'
                : candidateLoadError
                ? '用户列表读取失败'
                : searchText.trim()
                ? '未找到匹配的可邀请用户'
                : '没有符合邀请条件的用户；请确认账号已启用且尚未加入此项目'
            }
            style={{ width: '100%' }}
            getPopupContainer={(trigger) =>
              trigger.parentElement || document.body
            }
          />
          {selectedUser ? (
            <div style={{ marginTop: 12 }}>
              <Typography.Text strong>邀请对象：</Typography.Text>
              <Typography.Text>
                {selectedUser.displayName || selectedUser.username}
              </Typography.Text>
              <Typography.Text type="secondary">
                （@{selectedUser.username}）
              </Typography.Text>
              {selectedUser.invitationPending ? (
                <Alert
                  type="info"
                  showIcon
                  message="该用户已有待处理邀请，不能重复发送。"
                  style={{ marginTop: 10 }}
                />
              ) : (
                <div
                  style={{
                    display: 'flex',
                    flexWrap: 'wrap',
                    gap: 8,
                    alignItems: 'center',
                    marginTop: 10,
                  }}
                >
                  <Select
                    aria-label="项目角色"
                    value={inviteRole}
                    onChange={setInviteRole}
                    disabled={submitting}
                    options={inviteRoles.map((role) => ({
                      value: role,
                      label:
                        role === 'ADMIN'
                          ? '管理员 — 管理项目成员'
                          : role === 'EDITOR'
                          ? '编辑者 — 修改图谱内容'
                          : '只读成员 — 查看和查询图谱',
                    }))}
                    style={{ minWidth: 220, flex: 1 }}
                  />
                  <Button
                    type="primary"
                    loading={submitting}
                    disabled={Boolean(
                      candidateLoadError || unknownInviteUserId,
                    )}
                    onClick={() => void submitInvitation()}
                  >
                    发送邀请
                  </Button>
                </div>
              )}
            </div>
          ) : null}
        </section>
      ) : null}
    </Modal>
  );
};

export default ProjectMembersDialog;
