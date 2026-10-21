import AdminPage from '@/features/admin/AdminPage';
import { applyAdminFormError } from '@/features/admin/applyFormError';
import CopyableValue from '@/features/admin/CopyableValue';
import {
  ADMIN_TIME_ZONE_LABEL,
  formatAdminDateTime,
} from '@/features/admin/dateTime';
import OneTimeValuesModal from '@/features/admin/OneTimeValuesModal';
import {
  createOAuthClient,
  disableOAuthClient,
  getOAuthClients,
  type AppClient,
  type CreatedAppClient,
} from '@/features/admin/service';
import { useAdminRequestError } from '@/features/admin/useAdminRequestError';
import { useCurrentUser } from '@/features/auth/context';
import { normalizeApiError } from '@/features/errors/apiError';
import { ApiOutlined, CopyOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Checkbox,
  Descriptions,
  Drawer,
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

const SCOPE_OPTIONS = [
  { value: 'profile', label: '基本身份', description: 'profile' },
  { value: 'ontology.read', label: '图谱读取', description: 'ontology.read' },
  { value: 'ontology.write', label: '图谱修改', description: 'ontology.write' },
  {
    value: 'ontology.manage',
    label: '项目与成员管理',
    description: 'ontology.manage',
  },
];

type CreateClientFields = {
  displayName: string;
  redirectUri: string;
  scopes: string[];
};

async function copyValue(value: string): Promise<void> {
  if (!navigator.clipboard?.writeText) throw new Error('clipboard unavailable');
  await navigator.clipboard.writeText(value);
}

const AdminOAuthClientsPage: React.FC = () => {
  const actor = useCurrentUser();
  const { message } = App.useApp();
  const resolveFailure = useAdminRequestError();
  const [createForm] = Form.useForm<CreateClientFields>();
  const [items, setItems] = useState<AppClient[]>([]);
  const [loading, setLoading] = useState(false);
  const [hasLoaded, setHasLoaded] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [writeNotice, setWriteNotice] = useState<{
    type: 'warning' | 'success';
    message: string;
    client?: AppClient;
  }>();
  const requestSequence = useRef(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [checkingCreateOutcome, setCheckingCreateOutcome] = useState(false);
  const [createOutcomeUnknown, setCreateOutcomeUnknown] =
    useState<CreateClientFields>();
  const [createOutcomeChecked, setCreateOutcomeChecked] = useState(false);
  const [createError, setCreateError] = useState('');
  const [created, setCreated] = useState<CreatedAppClient>();
  const [selectedClient, setSelectedClient] = useState<AppClient>();
  const [disableTarget, setDisableTarget] = useState<AppClient>();
  const [disableError, setDisableError] = useState('');
  const [disableOutcomeUnknown, setDisableOutcomeUnknown] = useState(false);
  const [checkingDisableOutcome, setCheckingDisableOutcome] = useState(false);
  const [busyClientId, setBusyClientId] = useState<string>();
  const [copyError, setCopyError] = useState('');

  const load = async () => {
    const sequence = ++requestSequence.current;
    setLoading(true);
    setLoadError('');
    try {
      const result = await getOAuthClients();
      if (sequence !== requestSequence.current) return;
      setItems(result || []);
      setHasLoaded(true);
      return result || [];
    } catch (error) {
      if (sequence !== requestSequence.current) return;
      const failure = resolveFailure(error);
      if (failure.accessLost) {
        setItems([]);
        setCreated(undefined);
        setSelectedClient(undefined);
      }
      setLoadError(failure.message);
      setHasLoaded(true);
      return undefined;
    } finally {
      if (sequence === requestSequence.current) setLoading(false);
    }
  };

  useEffect(() => {
    if (actor.systemAdmin) void load();
    return () => {
      requestSequence.current += 1;
    };
  }, [actor.systemAdmin]);

  if (!actor.systemAdmin) {
    return (
      <AdminPage title="OAuth 客户端" description="此页面仅供系统管理员使用。">
        <Result status="403" title="无权访问" />
      </AdminPage>
    );
  }

  const reportCopyError = (text: string) => setCopyError(text);
  const reportCopySuccess = (text: string) => {
    setCopyError('');
    message.success(text);
  };
  const copyInDrawer = async (value: string, label: string) => {
    setCopyError('');
    try {
      await copyValue(value);
      message.success(`${label}已复制。`);
    } catch {
      setCopyError(`复制${label}失败，请在上方文本框中手动选择并复制完整值。`);
    }
  };

  const verifyCreateOutcome = async () => {
    if (!createOutcomeUnknown) return;
    const attempted = createOutcomeUnknown;
    setCheckingCreateOutcome(true);
    try {
      const refreshed = await load();
      const match = refreshed?.find(
        (client) =>
          client.displayName === attempted.displayName &&
          client.redirectUri === attempted.redirectUri,
      );
      if (match) {
        setCreateOutcomeUnknown(undefined);
        setCreateOpen(false);
        setCreateError('');
        createForm.resetFields();
        setWriteNotice({
          type: 'warning',
          client: match,
          message: `已找到匹配的客户端“${match.displayName}”，但创建响应未能确认，Secret 无法再次取回。请停用该记录并重新注册以生成新的 Secret。`,
        });
      } else if (refreshed) {
        setCreateOutcomeChecked(true);
        setCreateError(
          '已刷新完整客户端列表，未找到名称和回调地址均匹配的记录。没有自动重试；请确认列表已更新后再由你决定是否再次提交。',
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
      const refreshed = await load();
      const current = refreshed?.find((client) => client.id === target.id);
      if (current && !current.enabled) {
        setDisableOutcomeUnknown(false);
        setDisableTarget(undefined);
        setDisableError('');
        message.success('重新读取已确认客户端处于停用状态。');
      } else if (current?.enabled) {
        setDisableOutcomeUnknown(false);
        setDisableTarget(current);
        setDisableError(
          '重新读取已确认客户端仍处于启用状态；没有自动重试。若仍需停用，请再次明确点击“停用客户端”。',
        );
      } else if (refreshed) {
        setDisableOutcomeUnknown(true);
        setDisableError(
          '已重新读取客户端列表，但未找到目标记录；操作结果仍未确认，请先刷新并定位该客户端。',
        );
      }
    } finally {
      setCheckingDisableOutcome(false);
    }
  };

  const saveCreatedClient = async (values: CreateClientFields) => {
    setCreating(true);
    setCreateError('');
    try {
      const result = await createOAuthClient(values);
      setCreated(result);
      setCreateOpen(false);
      createForm.resetFields();
      // Secret 只在创建响应里出现，先保存在一次性结果界面，再刷新客户端列表。
      void load();
    } catch (error) {
      const appError = normalizeApiError(error);
      const formError = applyAdminFormError(error, createForm);
      if (appError.status === 401) {
        resolveFailure(error);
        setCreated(undefined);
        setCreateOpen(false);
      } else if (appError.status === 403) {
        setItems([]);
        setCreated(undefined);
        setCreateOpen(false);
        setLoadError(appError.message);
      } else if (appError.writeOutcomeUnknown) {
        setCreateOutcomeUnknown(values);
        setCreateOutcomeChecked(false);
        setCreateError(
          `${appError.message} 请先刷新客户端列表核实是否已创建，不要立即重复提交。`,
        );
      } else {
        setCreateError(formError);
      }
    } finally {
      setCreating(false);
    }
  };

  const disableClient = async () => {
    if (!disableTarget) return;
    const target = disableTarget;
    setBusyClientId(target.id);
    setDisableError('');
    setDisableOutcomeUnknown(false);
    try {
      await disableOAuthClient(target.id);
      setItems((current) =>
        current.map((item) =>
          item.id === target.id ? { ...item, enabled: false } : item,
        ),
      );
      setSelectedClient((current) =>
        current?.id === target.id ? { ...current, enabled: false } : current,
      );
      setDisableTarget(undefined);
      message.success('客户端已停用，现有 App 授权已撤销。');
    } catch (error) {
      const failure = resolveFailure(error);
      if (failure.accessLost) {
        setItems([]);
        setSelectedClient(undefined);
      }
      if (failure.redirectedToLogin) {
        setDisableTarget(undefined);
      } else {
        setDisableError(failure.message);
        if (normalizeApiError(error).writeOutcomeUnknown) {
          setDisableOutcomeUnknown(true);
          setDisableError(
            `${failure.message} 请重新读取客户端状态；系统不会自动重试。`,
          );
        }
      }
    } finally {
      setBusyClientId(undefined);
    }
  };

  const columns: ColumnsType<AppClient> = [
    {
      title: '应用',
      dataIndex: 'displayName',
      width: 200,
      render: (value: string) => (
        <Typography.Text strong ellipsis>
          {value}
        </Typography.Text>
      ),
    },
    {
      title: 'Client ID',
      dataIndex: 'clientId',
      width: 260,
      render: (value: string) => (
        <CopyableValue
          value={value}
          label="Client ID"
          onCopyError={reportCopyError}
          onCopySuccess={reportCopySuccess}
        />
      ),
    },
    {
      title: '回调地址',
      dataIndex: 'redirectUri',
      width: 260,
      ellipsis: true,
      render: (value: string) => (
        <Tooltip title="完整地址可在客户端详情中查看和复制" placement="topLeft">
          <Typography.Text ellipsis>{value}</Typography.Text>
        </Tooltip>
      ),
    },
    {
      title: '允许申请的权限',
      dataIndex: 'scopes',
      width: 250,
      render: (scopes: string[]) => (
        <Space wrap size={[4, 4]}>
          {scopes.map((scope) => {
            const known = SCOPE_OPTIONS.find((item) => item.value === scope);
            return (
              <Tag key={scope}>
                {known ? known.label : `其他权限（${scope}）`}
              </Tag>
            );
          })}
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 110,
      render: (enabled: boolean) => (
        <Tag color={enabled ? 'green' : 'default'}>
          {enabled ? '已启用' : '已停用'}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_, client) => (
        <Space>
          <Button
            type="link"
            onClick={() => {
              setCopyError('');
              setSelectedClient(client);
            }}
          >
            查看详情
          </Button>
          {client.enabled ? (
            <Button
              type="link"
              danger
              loading={busyClientId === client.id}
              disabled={Boolean(busyClientId)}
              onClick={() => {
                setDisableError('');
                setDisableTarget(client);
              }}
            >
              停用客户端
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  const pagination: TablePaginationConfig = {
    pageSize: 20,
    hideOnSinglePage: true,
    showSizeChanger: items.length > 20,
    showTotal: (count) => `共 ${count} 条`,
  };

  const showTable = loading || items.length > 0 || !loadError;
  const detail = selectedClient;

  return (
    <AdminPage
      title="OAuth 客户端"
      description="管理允许访问 Ontology 的应用，例如 Workbench。权限范围是可申请上限，实际操作仍受当前用户的项目权限控制。"
      actions={
        <Button
          type="primary"
          icon={<ApiOutlined aria-hidden="true" />}
          onClick={() => {
            setCreateError('');
            setCreateOpen(true);
          }}
        >
          注册客户端
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
              ? '客户端列表未更新，当前仍显示上次读取的数据。'
              : '客户端列表读取失败'
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
            writeNotice.client ? (
              <Button onClick={() => setSelectedClient(writeNotice.client)}>
                查看疑似客户端
              </Button>
            ) : undefined
          }
          closable
          onClose={() => setWriteNotice(undefined)}
        />
      ) : null}
      {copyError && !selectedClient ? (
        <Alert
          style={{ marginBottom: 16 }}
          type="error"
          showIcon
          message={copyError}
        />
      ) : null}
      {showTable ? (
        <Table<AppClient>
          rowKey="id"
          columns={columns}
          dataSource={items}
          loading={loading}
          locale={{ emptyText: '暂无 OAuth 客户端' }}
          scroll={{ x: 1240 }}
          pagination={pagination}
        />
      ) : null}

      <Modal
        title="注册 OAuth 客户端"
        open={createOpen}
        onCancel={() => {
          if (!creating && !createOutcomeUnknown) setCreateOpen(false);
        }}
        closable={!creating && !createOutcomeUnknown}
        keyboard={!creating && !createOutcomeUnknown}
        maskClosable={!creating && !createOutcomeUnknown}
        width="min(560px, calc(100vw - 24px))"
        footer={null}
        destroyOnClose
      >
        <Form
          form={createForm}
          layout="vertical"
          initialValues={{ scopes: ['profile', 'ontology.read'] }}
          onFinish={saveCreatedClient}
          preserve
        >
          <Form.Item
            name="displayName"
            label="应用名称"
            rules={[
              { required: true, whitespace: true, message: '请输入应用名称。' },
            ]}
          >
            <Input maxLength={160} />
          </Form.Item>
          <Form.Item
            name="redirectUri"
            label="HTTPS 回调地址"
            extra="必须是完整 HTTPS 地址，不能包含凭据或 URL 片段。"
            rules={[
              { required: true, message: '请输入回调地址。' },
              {
                validator: async (_, value: string) => {
                  if (!value) return Promise.resolve();
                  try {
                    const uri = new URL(value);
                    if (
                      uri.protocol !== 'https:' ||
                      !uri.hostname ||
                      uri.username ||
                      uri.password ||
                      uri.hash ||
                      value.length > 2048 ||
                      value.includes('*') ||
                      /\s/.test(value)
                    ) {
                      throw new Error('invalid redirect URI');
                    }
                    return Promise.resolve();
                  } catch {
                    return Promise.reject(
                      new Error(
                        '请输入有效 HTTPS 地址；不允许通配符、凭据或 URL 片段。',
                      ),
                    );
                  }
                },
              },
            ]}
          >
            <Input
              maxLength={2048}
              placeholder="https://app.example.com/oauth/callback"
              autoComplete="url"
            />
          </Form.Item>
          <Form.Item
            name="scopes"
            label="允许申请的权限"
            extra="至少包含基本身份 profile。"
            rules={[
              {
                validator: async (_, values: string[]) =>
                  values?.includes('profile')
                    ? Promise.resolve()
                    : Promise.reject(
                        new Error('至少选择基本身份（profile）。'),
                      ),
              },
            ]}
          >
            <Checkbox.Group style={{ display: 'grid', gap: 8 }}>
              {SCOPE_OPTIONS.map((scope) => (
                <Checkbox key={scope.value} value={scope.value}>
                  <Space size={6} wrap>
                    <span>{scope.label}</span>
                    <Typography.Text type="secondary" code>
                      {scope.description}
                    </Typography.Text>
                  </Space>
                </Checkbox>
              ))}
            </Checkbox.Group>
          </Form.Item>
          <Typography.Paragraph type="secondary">
            客户端使用授权码 + PKCE。Client Secret 只在创建完成时显示一次。
          </Typography.Paragraph>
          {createError ? (
            <Alert
              role="alert"
              type="error"
              showIcon
              message={createError}
              action={
                createOutcomeUnknown ? (
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
                              '若服务端列表尚未反映最新结果，继续提交可能创建重复客户端。确认后仍需再次手动点击创建。',
                            okText: '我已核对，继续',
                            cancelText: '继续核对',
                            onOk: () => {
                              setCreateOutcomeUnknown(undefined);
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
              disabled={creating || Boolean(createOutcomeUnknown)}
            >
              取消
            </Button>
            <Button
              type="primary"
              htmlType="submit"
              loading={creating}
              disabled={Boolean(createOutcomeUnknown)}
            >
              创建客户端
            </Button>
          </Space>
        </Form>
      </Modal>

      <Modal
        title="停用客户端"
        open={Boolean(disableTarget)}
        onCancel={() => {
          if (!busyClientId) {
            setDisableTarget(undefined);
            setDisableError('');
            setDisableOutcomeUnknown(false);
          }
        }}
        onOk={() => void disableClient()}
        okText="停用客户端"
        cancelText="取消"
        okButtonProps={{
          danger: true,
          disabled: !disableTarget || busyClientId === disableTarget?.id,
        }}
        confirmLoading={Boolean(
          disableTarget && busyClientId === disableTarget.id,
        )}
        closable={!busyClientId}
        keyboard={!busyClientId}
        maskClosable={!busyClientId}
        destroyOnClose
      >
        {disableTarget ? (
          <>
            <Typography.Paragraph>
              确定停用{' '}
              <Typography.Text strong>
                {disableTarget.displayName}
              </Typography.Text>{' '}
              吗？此操作会撤销该客户端当前所有用户授权。
            </Typography.Paragraph>
            {disableError ? (
              <Alert
                role="alert"
                type="error"
                showIcon
                message="客户端状态需要核对"
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

      <Drawer
        title="OAuth 客户端详情"
        open={Boolean(detail)}
        placement="right"
        width="min(560px, 100vw)"
        onClose={() => {
          setSelectedClient(undefined);
          setCopyError('');
        }}
        destroyOnClose
      >
        {detail ? (
          <>
            {!detail.enabled ? (
              <Alert
                style={{ marginBottom: 16 }}
                type="warning"
                showIcon
                message="客户端已停用，不能再用于新连接；相关授权已撤销。"
              />
            ) : null}
            {copyError ? (
              <Alert
                style={{ marginBottom: 16 }}
                type="error"
                showIcon
                message={copyError}
              />
            ) : null}
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="应用名称">
                {detail.displayName}
              </Descriptions.Item>
              <Descriptions.Item label="Client ID">
                <Space direction="vertical" style={{ width: '100%' }}>
                  <Input
                    aria-label="完整 Client ID"
                    value={detail.clientId}
                    readOnly
                    onFocus={(event) => event.currentTarget.select()}
                  />
                  <Button
                    icon={<CopyOutlined aria-hidden="true" />}
                    onClick={() =>
                      void copyInDrawer(detail.clientId, 'Client ID')
                    }
                  >
                    复制 Client ID
                  </Button>
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="回调地址">
                <Space direction="vertical" style={{ width: '100%' }}>
                  <Input.TextArea
                    aria-label="完整回调地址"
                    value={detail.redirectUri}
                    readOnly
                    autoSize={{ minRows: 2, maxRows: 5 }}
                    onFocus={(event) => event.currentTarget.select()}
                    style={{ overflowWrap: 'anywhere' }}
                  />
                  <Button
                    icon={<CopyOutlined aria-hidden="true" />}
                    onClick={() =>
                      void copyInDrawer(detail.redirectUri, '回调地址')
                    }
                  >
                    复制回调地址
                  </Button>
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="允许申请的权限">
                <Space wrap>
                  {detail.scopes.map((scope) => {
                    const known = SCOPE_OPTIONS.find(
                      (item) => item.value === scope,
                    );
                    return (
                      <Tag key={scope}>
                        {known
                          ? `${known.label}（${scope}）`
                          : `其他权限（${scope}）`}
                      </Tag>
                    );
                  })}
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                {detail.enabled ? '已启用' : '已停用'}
              </Descriptions.Item>
              <Descriptions.Item
                label={
                  <Tooltip title={ADMIN_TIME_ZONE_LABEL}>
                    创建时间（北京时间）
                  </Tooltip>
                }
              >
                {formatAdminDateTime(detail.createdAt)}
              </Descriptions.Item>
            </Descriptions>
          </>
        ) : null}
      </Drawer>

      <OneTimeValuesModal
        title="客户端凭证（仅此一次）"
        description="请立即将 Client Secret 保存到 Workbench 的受保护配置中。Client ID 也可在客户端详情中再次查看；Secret 不会再次返回。关闭后本页不会保留这两个值。"
        values={
          created
            ? [
                {
                  key: 'client-id',
                  label: 'Client ID',
                  value: created.clientId,
                },
                {
                  key: 'client-secret',
                  label: 'Client Secret',
                  value: created.clientSecret,
                  sensitive: true,
                },
              ]
            : []
        }
        open={Boolean(created)}
        onClose={() => setCreated(undefined)}
      />
    </AdminPage>
  );
};

export default AdminOAuthClientsPage;
