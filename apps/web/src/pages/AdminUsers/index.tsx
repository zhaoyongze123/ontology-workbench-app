import AdminPage from '@/features/admin/AdminPage';
import { applyAdminFormError } from '@/features/admin/applyFormError';
import {
  ADMIN_TIME_ZONE_LABEL,
  formatAdminDateTime,
} from '@/features/admin/dateTime';
import OneTimeValuesModal, {
  type OneTimeValue,
} from '@/features/admin/OneTimeValuesModal';
import {
  createActivationLink,
  createResetLink,
  createUser,
  getUsers,
  setUserEnabled,
  type ManagedUser,
} from '@/features/admin/service';
import { useAdminRequestError } from '@/features/admin/useAdminRequestError';
import { useCurrentUser } from '@/features/auth/context';
import { normalizeApiError } from '@/features/errors/apiError';
import {
  Alert,
  App,
  Button,
  Form,
  Input,
  Modal,
  Result,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'umi';

type CreateUserFields = { username: string; displayName: string };

const AdminUsersPage: React.FC = () => {
  const actor = useCurrentUser();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const resolveFailure = useAdminRequestError();
  const [createForm] = Form.useForm<CreateUserFields>();
  const [rows, setRows] = useState<ManagedUser[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [hasLoaded, setHasLoaded] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [writeNotice, setWriteNotice] = useState<{
    type: 'info' | 'warning' | 'success';
    message: string;
    checkUser?: { id: string; expectedEnabled: boolean };
  }>();
  const requestSequence = useRef(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [createBusy, setCreateBusy] = useState(false);
  const [checkingCreateOutcome, setCheckingCreateOutcome] = useState(false);
  const [createOutcomeUnknownUsername, setCreateOutcomeUnknownUsername] =
    useState<string>();
  const [createOutcomeChecked, setCreateOutcomeChecked] = useState(false);
  const [createError, setCreateError] = useState('');
  const [linkUserId, setLinkUserId] = useState<string>();
  const [credentialTitle, setCredentialTitle] = useState('');
  const [credentialDescription, setCredentialDescription] = useState('');
  const [oneTimeValues, setOneTimeValues] = useState<OneTimeValue[]>([]);
  const [disableTarget, setDisableTarget] = useState<ManagedUser | null>(null);
  const [disableError, setDisableError] = useState('');
  const [checkingDisableOutcome, setCheckingDisableOutcome] = useState(false);
  const [disableOutcomeUnknown, setDisableOutcomeUnknown] = useState(false);
  const [rowMutationId, setRowMutationId] = useState<string>();

  const load = async (nextPage = page, nextSize = size) => {
    const sequence = ++requestSequence.current;
    setLoading(true);
    setLoadError('');
    try {
      const result = await getUsers(nextPage, nextSize);
      if (sequence !== requestSequence.current) return;
      setRows(result.items || []);
      setTotal(result.total || 0);
      setPage(result.page ?? nextPage);
      setSize(result.size ?? nextSize);
      setHasLoaded(true);
      return result.items || [];
    } catch (error) {
      if (sequence !== requestSequence.current) return;
      const failure = resolveFailure(error);
      if (failure.redirectedToLogin || failure.accessLost) {
        setRows([]);
        setOneTimeValues([]);
      }
      setLoadError(failure.message);
      setHasLoaded(true);
      return undefined;
    } finally {
      if (sequence === requestSequence.current) setLoading(false);
    }
  };

  useEffect(() => {
    if (actor.systemAdmin) void load(0, 20);
    return () => {
      requestSequence.current += 1;
    };
  }, [actor.systemAdmin]);

  if (!actor.systemAdmin) {
    return (
      <AdminPage title="用户管理" description="此页面仅供系统管理员使用。">
        <Result status="403" title="无权访问" />
      </AdminPage>
    );
  }

  const showOneTimeValue = (
    title: string,
    description: string,
    label: string,
    value: string,
  ) => {
    setCredentialTitle(title);
    setCredentialDescription(description);
    setOneTimeValues([{ key: 'account-action-link', label, value }]);
  };

  const handleAccountLink = async (user: ManagedUser) => {
    setLinkUserId(user.id);
    try {
      if (user.passwordActivated) {
        const result = await createResetLink(user.id);
        showOneTimeValue(
          '密码重置链接已生成',
          '请通过已核实身份的安全渠道交付。用户提交新密码后才会完成重置；关闭后本页不会保留此链接。',
          '密码重置链接',
          result.resetUrl,
        );
      } else {
        const result = await createActivationLink(user.id);
        showOneTimeValue(
          '新的激活链接已生成',
          '旧的激活链接已失效。请通过已核实身份的安全渠道交付；关闭后本页不会保留此链接。',
          '账号激活链接',
          result.activationUrl,
        );
      }
    } catch (error) {
      const failure = resolveFailure(error);
      if (failure.accessLost) {
        setRows([]);
        setOneTimeValues([]);
        setLoadError(failure.message);
      } else if (!failure.redirectedToLogin) {
        if (normalizeApiError(error).writeOutcomeUnknown) {
          setWriteNotice({
            type: 'warning',
            message: `${failure.message} ${
              user.passwordActivated
                ? '若重新生成重置链接，先前链接可能失效。'
                : '若重新生成激活链接，旧链接会失效。'
            } 请先确认是否需要再次生成。`,
          });
        } else {
          message.error(failure.message);
        }
      }
    } finally {
      setLinkUserId(undefined);
    }
  };

  const verifyUserState = async (userId: string, expectedEnabled: boolean) => {
    const refreshed = await load(page, size);
    const current = refreshed?.find((user) => user.id === userId);
    if (!current) {
      setWriteNotice({
        type: 'warning',
        message:
          '已重新读取当前页，但未找到目标账号；操作结果仍未确认，请先定位该用户再决定是否重试。',
      });
      return;
    }
    setWriteNotice(
      current.enabled === expectedEnabled
        ? {
            type: 'success',
            message: `已重新读取并确认账号状态为“${
              expectedEnabled ? '已启用' : '已停用'
            }”。`,
          }
        : {
            type: 'info',
            message: `已重新读取并确认账号仍为“${
              current.enabled ? '已启用' : '已停用'
            }”；如仍需操作，请由你明确发起。`,
          },
    );
  };

  const verifyCreateOutcome = async () => {
    if (!createOutcomeUnknownUsername) return;
    setCheckingCreateOutcome(true);
    const username = createOutcomeUnknownUsername;
    try {
      const refreshed = await load(0, size);
      if (
        refreshed?.some(
          (user) => user.username.toLowerCase() === username.toLowerCase(),
        )
      ) {
        setCreateOutcomeUnknownUsername(undefined);
        setCreateOpen(false);
        createForm.resetFields();
        setCreateError('');
        setWriteNotice({
          type: 'warning',
          message: `已在重新读取的用户页中找到 ${username}。创建响应未能确认，因此没有再次提交；请从列表为该账号生成新的激活链接。`,
        });
      } else if (refreshed) {
        setCreateOutcomeChecked(true);
        setCreateError(
          '已重新读取第 1 页，但没有找到该用户名。用户接口采用服务端分页，这不能证明账号未创建；请继续核对后再由你决定是否重试。',
        );
      }
    } finally {
      setCheckingCreateOutcome(false);
    }
  };

  const verifyDisableOutcome = async () => {
    if (!disableTarget) return;
    const target = disableTarget;
    setCheckingDisableOutcome(true);
    try {
      const refreshed = await load(page, size);
      const current = refreshed?.find((user) => user.id === target.id);
      if (current?.enabled === false) {
        setDisableOutcomeUnknown(false);
        setDisableTarget(null);
        setDisableError('');
        message.success('重新读取已确认用户处于停用状态。');
      } else if (current?.enabled === true) {
        setDisableOutcomeUnknown(false);
        setDisableTarget(current);
        setDisableError(
          '重新读取已确认用户仍处于启用状态；没有自动重试。若仍需停用，请再次明确点击“停用用户”。',
        );
      } else if (refreshed) {
        setDisableOutcomeUnknown(true);
        setDisableError(
          '已重新读取当前页，但未找到目标账号；操作结果仍未确认，请先定位该用户。',
        );
      }
    } finally {
      setCheckingDisableOutcome(false);
    }
  };

  const updateUser = (updated: ManagedUser) => {
    setRows((current) =>
      current.map((user) => (user.id === updated.id ? updated : user)),
    );
  };

  const enableUser = async (user: ManagedUser) => {
    setRowMutationId(user.id);
    try {
      const updated = await setUserEnabled(user.id, true);
      updateUser(updated);
      message.success('账号已启用');
    } catch (error) {
      const failure = resolveFailure(error);
      if (failure.accessLost) {
        setRows([]);
        setLoadError(failure.message);
      } else if (!failure.redirectedToLogin) {
        const appError = normalizeApiError(error);
        if (appError.writeOutcomeUnknown) {
          setWriteNotice({
            type: 'warning',
            message: `${failure.message}请重新读取用户状态后再决定是否重试。`,
            checkUser: { id: user.id, expectedEnabled: true },
          });
        } else {
          message.error(failure.message);
        }
      }
    } finally {
      setRowMutationId(undefined);
    }
  };

  const disableUser = async () => {
    if (!disableTarget) return;
    const target = disableTarget;
    setRowMutationId(target.id);
    setDisableError('');
    setDisableOutcomeUnknown(false);
    try {
      const updated = await setUserEnabled(target.id, false);
      updateUser(updated);
      setDisableTarget(null);
      message.success(
        target.id === actor.id
          ? '当前账号已停用，正在退出登录。'
          : '用户已停用，目标账号的会话和 App 授权已撤销。',
      );
      if (target.id === actor.id) navigate('/login', { replace: true });
    } catch (error) {
      const failure = resolveFailure(error);
      if (failure.accessLost) {
        setRows([]);
        setOneTimeValues([]);
      }
      if (failure.redirectedToLogin) {
        setDisableTarget(null);
        setOneTimeValues([]);
      } else {
        // 最后管理员等业务冲突留在确认框内，目标行仍保持服务端原状态。
        setDisableError(failure.message);
        if (normalizeApiError(error).writeOutcomeUnknown) {
          setDisableOutcomeUnknown(true);
          setDisableError(
            `${failure.message} 请点击下方按钮重新读取状态；系统不会自动重试。`,
          );
        }
      }
    } finally {
      setRowMutationId(undefined);
    }
  };

  const submitCreateUser = async (values: CreateUserFields) => {
    setCreateBusy(true);
    setCreateError('');
    createForm.setFields([
      { name: 'username', errors: [] },
      { name: 'displayName', errors: [] },
    ]);
    try {
      const result = await createUser(
        values.username.trim(),
        values.displayName.trim(),
      );
      // 先保存在頁面內存，再刷新列表；刷新失败不能丢失服务端只返回一次的激活链接。
      showOneTimeValue(
        '账号已创建，激活链接仅展示一次',
        '请通过已核实身份的安全渠道交付。关闭后本页不会保留此链接。',
        '账号激活链接',
        result.activationUrl,
      );
      setCreateOpen(false);
      createForm.resetFields();
      void load(0, size);
    } catch (error) {
      const appError = normalizeApiError(error);
      const formError = applyAdminFormError(error, createForm);
      if (appError.status === 401) {
        resolveFailure(error);
        setOneTimeValues([]);
        setCreateOpen(false);
      } else if (appError.status === 403) {
        setRows([]);
        setOneTimeValues([]);
        setCreateOpen(false);
        setLoadError(appError.message);
      } else if (appError.writeOutcomeUnknown) {
        setCreateOutcomeUnknownUsername(values.username.trim());
        setCreateOutcomeChecked(false);
        setCreateError(
          `${appError.message} 请先刷新用户列表核对账号是否已创建，避免重复提交。`,
        );
      } else {
        setCreateError(formError);
      }
    } finally {
      setCreateBusy(false);
    }
  };

  const columns: ColumnsType<ManagedUser> = [
    {
      title: '用户',
      key: 'user',
      width: 220,
      render: (_, user) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>
            {user.displayName || user.username}
          </Typography.Text>
          <Typography.Text type="secondary">{user.username}</Typography.Text>
        </Space>
      ),
    },
    {
      title: '账号状态',
      dataIndex: 'enabled',
      width: 130,
      render: (enabled: boolean) => (
        <Tag color={enabled ? 'green' : 'default'}>
          {enabled ? '已启用' : '已停用'}
        </Tag>
      ),
    },
    {
      title: '本地密码',
      dataIndex: 'passwordActivated',
      width: 130,
      render: (activated: boolean) => (
        <Tag color={activated ? 'green' : 'gold'}>
          {activated ? '已设置' : '待设置'}
        </Tag>
      ),
    },
    {
      title: '系统角色',
      dataIndex: 'systemAdmin',
      width: 150,
      render: (systemAdmin: boolean) => (
        <Tag color={systemAdmin ? 'blue' : 'default'}>
          {systemAdmin ? '系统管理员' : '普通用户'}
        </Tag>
      ),
    },
    {
      title: (
        <Tooltip title={ADMIN_TIME_ZONE_LABEL}>创建时间（北京时间）</Tooltip>
      ),
      dataIndex: 'createTime',
      width: 190,
      render: (value: string) => formatAdminDateTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 300,
      render: (_, user) => (
        <Space wrap>
          <Button
            type="link"
            loading={linkUserId === user.id}
            onClick={() => void handleAccountLink(user)}
          >
            {user.passwordActivated ? '生成密码重置链接' : '重新生成激活链接'}
          </Button>
          {user.enabled ? (
            <Button
              type="link"
              danger
              loading={
                rowMutationId === user.id && disableTarget?.id === user.id
              }
              disabled={rowMutationId === user.id}
              onClick={() => {
                setDisableError('');
                setDisableTarget(user);
              }}
            >
              停用用户
            </Button>
          ) : (
            <Button
              type="link"
              loading={rowMutationId === user.id}
              onClick={() => void enableUser(user)}
            >
              启用用户
            </Button>
          )}
        </Space>
      ),
    },
  ];

  const pagination: TablePaginationConfig = {
    current: page + 1,
    pageSize: size,
    total,
    disabled: loading,
    hideOnSinglePage: true,
    showSizeChanger: total > size,
    showTotal: (count) => `共 ${count} 条`,
    onChange: (currentPage, nextSize) => void load(currentPage - 1, nextSize),
  };

  const showTable = loading || rows.length > 0 || !loadError;
  const targetLabel = disableTarget
    ? `${disableTarget.displayName || disableTarget.username}（${
        disableTarget.username
      }）`
    : '';

  return (
    <AdminPage
      title="用户管理"
      description="查看并维护账号启用状态、本地密码状态和系统角色。项目内角色由各项目成员设置决定。"
      actions={
        <Button
          type="primary"
          onClick={() => {
            setCreateError('');
            setCreateOpen(true);
          }}
        >
          创建用户
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
              ? '用户列表未更新，当前仍显示上次读取的数据。'
              : '用户列表读取失败'
          }
          description={loadError}
          action={
            <Button disabled={loading} onClick={() => void load()}>
              重试
            </Button>
          }
        />
      ) : null}
      {writeNotice ? (
        <Alert
          style={{ marginBottom: 16 }}
          type={writeNotice.type}
          showIcon
          message={writeNotice.message}
          action={
            writeNotice.checkUser ? (
              <Button
                disabled={loading}
                onClick={() =>
                  void verifyUserState(
                    writeNotice.checkUser!.id,
                    writeNotice.checkUser!.expectedEnabled,
                  )
                }
              >
                重新读取用户状态
              </Button>
            ) : undefined
          }
          closable
          onClose={() => setWriteNotice(undefined)}
        />
      ) : null}
      {showTable ? (
        <Table<ManagedUser>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={loading}
          locale={{ emptyText: '暂无用户' }}
          scroll={{ x: 1120 }}
          pagination={pagination}
        />
      ) : null}

      <Modal
        title="创建用户"
        open={createOpen}
        onCancel={() => {
          if (!createBusy && !createOutcomeUnknownUsername)
            setCreateOpen(false);
        }}
        closable={!createBusy && !createOutcomeUnknownUsername}
        keyboard={!createBusy && !createOutcomeUnknownUsername}
        maskClosable={!createBusy && !createOutcomeUnknownUsername}
        width="min(560px, calc(100vw - 24px))"
        confirmLoading={createBusy}
        footer={null}
        destroyOnClose
      >
        <Form
          form={createForm}
          layout="vertical"
          onFinish={submitCreateUser}
          preserve
        >
          <Form.Item
            name="username"
            label="用户名"
            rules={[
              { required: true, whitespace: true, message: '请输入用户名。' },
              {
                pattern: /^[A-Za-z0-9][A-Za-z0-9._@+-]{1,119}$/,
                message:
                  '请输入 2–120 位字母、数字或 . _ @ + -，并以字母或数字开头。',
              },
            ]}
          >
            <Input autoComplete="off" maxLength={120} />
          </Form.Item>
          <Form.Item
            name="displayName"
            label="显示名称"
            rules={[
              { required: true, whitespace: true, message: '请输入显示名称。' },
            ]}
          >
            <Input maxLength={200} showCount />
          </Form.Item>
          {createError ? (
            <Alert
              role="alert"
              type="error"
              showIcon
              message={createError}
              action={
                createOutcomeUnknownUsername ? (
                  <Space wrap>
                    <Button
                      disabled={checkingCreateOutcome || loading}
                      onClick={() => void verifyCreateOutcome()}
                    >
                      {checkingCreateOutcome ? '正在核对' : '刷新列表核对'}
                    </Button>
                    {createOutcomeChecked ? (
                      <Button
                        onClick={() =>
                          Modal.confirm({
                            title: '创建结果仍未确认',
                            content:
                              '当前列表是分页结果，无法证明账号不存在。若继续提交，可能遇到重复账号；确认后仍需再次手动点击创建。',
                            okText: '我已核对，继续',
                            cancelText: '继续核对',
                            onOk: () => {
                              setCreateOutcomeUnknownUsername(undefined);
                              setCreateOutcomeChecked(false);
                              setCreateError('');
                            },
                          })
                        }
                      >
                        我已核对，仍要重试
                      </Button>
                    ) : null}
                  </Space>
                ) : undefined
              }
            />
          ) : null}
          <Space style={{ marginTop: 16 }}>
            <Button
              onClick={() => setCreateOpen(false)}
              disabled={createBusy || Boolean(createOutcomeUnknownUsername)}
            >
              取消
            </Button>
            <Button
              type="primary"
              htmlType="submit"
              loading={createBusy}
              disabled={Boolean(createOutcomeUnknownUsername)}
            >
              创建并生成激活链接
            </Button>
          </Space>
        </Form>
      </Modal>

      <Modal
        title="停用用户"
        open={Boolean(disableTarget)}
        onCancel={() => {
          if (!rowMutationId) {
            setDisableTarget(null);
            setDisableError('');
          }
        }}
        onOk={() => void disableUser()}
        okText="停用用户"
        cancelText="取消"
        okButtonProps={{
          danger: true,
          disabled: !disableTarget || rowMutationId === disableTarget?.id,
        }}
        confirmLoading={Boolean(
          disableTarget && rowMutationId === disableTarget.id,
        )}
        closable={!rowMutationId}
        keyboard={!rowMutationId}
        maskClosable={!rowMutationId}
        width="min(560px, calc(100vw - 24px))"
        destroyOnClose
      >
        {disableTarget ? (
          <>
            <Typography.Paragraph>
              确定停用 <Typography.Text strong>{targetLabel}</Typography.Text>{' '}
              吗？该账号将无法继续登录；现有会话和 App 授权会失效。
            </Typography.Paragraph>
            {disableTarget.id === actor.id ? (
              <Alert
                style={{ marginBottom: 16 }}
                type="warning"
                showIcon
                message="这是当前登录账号，操作成功后将返回登录页。"
              />
            ) : null}
            <Typography.Paragraph type="secondary">
              若这是最后一位启用中的系统管理员，服务端会拒绝此操作并说明原因。
            </Typography.Paragraph>
            {disableError ? (
              <Alert
                role="alert"
                type="error"
                showIcon
                message="停用结果需要核对"
                description={disableError}
                action={
                  disableOutcomeUnknown ? (
                    <Button
                      disabled={checkingDisableOutcome || loading}
                      onClick={() => void verifyDisableOutcome()}
                    >
                      {checkingDisableOutcome ? '正在核对' : '重新读取状态'}
                    </Button>
                  ) : undefined
                }
              />
            ) : null}
          </>
        ) : null}
      </Modal>

      <OneTimeValuesModal
        title={credentialTitle}
        description={credentialDescription}
        values={oneTimeValues}
        open={oneTimeValues.length > 0}
        onClose={() => setOneTimeValues([])}
      />
    </AdminPage>
  );
};

export default AdminUsersPage;
