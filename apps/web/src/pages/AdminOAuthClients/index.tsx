import AdminPage from '@/features/admin/AdminPage';
import { applyAdminFormError } from '@/features/admin/applyFormError';
import CopyableValue from '@/features/admin/CopyableValue';
import {
  ADMIN_TIME_ZONE_LABEL,
  formatAdminDateTime,
} from '@/features/admin/dateTime';
import OAuthClientConfigFields, {
  type OAuthClientConfigurationFields,
} from '@/features/admin/OAuthClientConfigFields';
import { oauthScopeLabel } from '@/features/admin/oauthScopePresentation';
import OneTimeValuesModal from '@/features/admin/OneTimeValuesModal';
import {
  createOAuthClient,
  disableOAuthClient,
  enableOAuthClient,
  getOAuthClients,
  rotateOAuthClientSecret,
  updateOAuthClient,
  type AppClient,
  type AppClientConfiguration,
  type CreatedAppClient,
  type RotatedAppClientSecret,
} from '@/features/admin/service';
import { useAdminRequestError } from '@/features/admin/useAdminRequestError';
import { useCurrentUser } from '@/features/auth/context';
import { normalizeApiError } from '@/features/errors/apiError';
import ListPagination from '@/features/lists/ListPagination';
import { ApiOutlined, CopyOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Descriptions,
  Divider,
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
import type { ColumnsType } from 'antd/es/table';
import React, { useEffect, useMemo, useRef, useState } from 'react';

type CreateClientFields = OAuthClientConfigurationFields;

type PendingCreateOutcome = {
  values: CreateClientFields;
  existingClientIds: string[];
};

function isCompleteClientList(value: unknown): value is AppClient[] {
  if (!Array.isArray(value)) return false;
  if (
    !value.every(
      (client) =>
        client !== null &&
        typeof client === 'object' &&
        typeof client.id === 'string' &&
        client.id.length > 0 &&
        typeof client.displayName === 'string' &&
        typeof client.redirectUri === 'string',
    )
  ) {
    return false;
  }
  const ids = value.map((client) => client?.id);
  return new Set(ids).size === ids.length;
}

async function copyValue(value: string): Promise<void> {
  if (!navigator.clipboard?.writeText) throw new Error('clipboard unavailable');
  await navigator.clipboard.writeText(value);
}

const AdminOAuthClientsPage: React.FC = () => {
  const actor = useCurrentUser();
  const { message } = App.useApp();
  const resolveFailure = useAdminRequestError();
  const [createForm] = Form.useForm<CreateClientFields>();
  const [editForm] = Form.useForm<OAuthClientConfigurationFields>();
  const tableContainerRef = useRef<HTMLDivElement>(null);
  const [tableWidth, setTableWidth] = useState(0);
  // 按管理页主内容的实际宽度切换列，避免 iframe 外壳和侧栏宽度造成误判。
  const compactLayout = tableWidth === 0 || tableWidth < 1080;
  const [items, setItems] = useState<AppClient[]>([]);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [hasLoaded, setHasLoaded] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [writeNotice, setWriteNotice] = useState<{
    type: 'warning' | 'success';
    message: string;
    client?: AppClient;
    actionLabel?: string;
    action?: 'view' | 'retry-secret-rotation';
  }>();
  const requestSequence = useRef(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [checkingCreateOutcome, setCheckingCreateOutcome] = useState(false);
  const [createOutcomeUnknown, setCreateOutcomeUnknown] =
    useState<PendingCreateOutcome>();
  const [createOutcomeChecked, setCreateOutcomeChecked] = useState(false);
  const [createError, setCreateError] = useState('');
  const [created, setCreated] = useState<CreatedAppClient>();
  const [rotatedSecret, setRotatedSecret] = useState<RotatedAppClientSecret>();
  const [selectedClient, setSelectedClient] = useState<AppClient>();
  const [editingClient, setEditingClient] = useState<AppClient>();
  const [editError, setEditError] = useState('');
  const [editOutcomeUnknown, setEditOutcomeUnknown] = useState<{
    clientId: string;
    values: OAuthClientConfigurationFields;
  }>();
  const [editOutcomeChecked, setEditOutcomeChecked] = useState(false);
  const [savingEdit, setSavingEdit] = useState(false);
  const [statusTarget, setStatusTarget] = useState<{
    client: AppClient;
    enabled: boolean;
  }>();
  const [statusError, setStatusError] = useState('');
  const [statusOutcomeUnknown, setStatusOutcomeUnknown] = useState(false);
  const [checkingStatusOutcome, setCheckingStatusOutcome] = useState(false);
  const [rotateTarget, setRotateTarget] = useState<AppClient>();
  const [rotateError, setRotateError] = useState('');
  const [rotateOutcomeUnknown, setRotateOutcomeUnknown] = useState(false);
  const [rotatingSecret, setRotatingSecret] = useState(false);
  const [busyClientId, setBusyClientId] = useState<string>();
  const [copyError, setCopyError] = useState('');

  const load = async () => {
    const sequence = ++requestSequence.current;
    setLoading(true);
    setLoadError('');
    try {
      const result = await getOAuthClients();
      if (!isCompleteClientList(result)) {
        throw new Error('客户端列表响应不完整');
      }
      if (sequence !== requestSequence.current) return;
      setItems(result || []);
      setSelectedClient((current) =>
        current
          ? result?.find((client) => client.id === current.id) || current
          : undefined,
      );
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
  const showTable = loading || items.length > 0 || (hasLoaded && !loadError);

  useEffect(() => {
    if (actor.systemAdmin) void load();
    return () => {
      requestSequence.current += 1;
    };
  }, [actor.systemAdmin]);

  useEffect(() => {
    const container = tableContainerRef.current;
    if (!container || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width;
      if (width !== undefined) setTableWidth(width);
    });
    observer.observe(container);
    return () => observer.disconnect();
  }, [showTable]);

  const orderedItems = useMemo(
    () =>
      [...items].sort((left, right) => {
        const byCreatedAt = right.createdAt.localeCompare(left.createdAt);
        return byCreatedAt || right.id.localeCompare(left.id);
      }),
    [items],
  );
  const pageRows = orderedItems.slice(
    (currentPage - 1) * pageSize,
    currentPage * pageSize,
  );
  useEffect(() => {
    const lastPage = Math.max(1, Math.ceil(items.length / pageSize));
    if (currentPage > lastPage) setCurrentPage(lastPage);
  }, [currentPage, items.length, pageSize]);

  if (!actor.systemAdmin) {
    return (
      <AdminPage title="OAuth 客户端">
        <Result status="403" title="无权访问" />
      </AdminPage>
    );
  }

  const reportCopyError = (text: string) => setCopyError(text);
  const reportCopySuccess = (text: string) => {
    setCopyError('');
    message.success(text);
  };
  const openNoticeClient = async () => {
    if (!writeNotice?.client) return;
    const target = writeNotice.client;
    const refreshed = await load();
    const current =
      refreshed?.find((client) => client.id === target.id) || target;
    if (writeNotice.action === 'retry-secret-rotation') {
      setRotateTarget(current);
      setRotateOutcomeUnknown(true);
      setRotateError(
        '上次轮换结果仍未确认，旧 Secret 可能已失效。请确认是否再次轮换并保存新值。',
      );
    } else {
      setSelectedClient(current);
    }
    setWriteNotice(undefined);
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
    const { values: attempted, existingClientIds } = createOutcomeUnknown;
    setCheckingCreateOutcome(true);
    try {
      const refreshed = await load();
      const matches = refreshed?.filter(
        (client) =>
          !existingClientIds.includes(client.id) &&
          client.displayName === attempted.displayName &&
          client.redirectUri === attempted.redirectUri,
      );
      if (matches?.length === 1) {
        const match = matches[0];
        setCreateOutcomeUnknown(undefined);
        setCreateOpen(false);
        setCreateError('');
        createForm.resetFields();
        setWriteNotice({
          type: 'warning',
          client: match,
          message: `发现一条提交后新增的匹配客户端“${match.displayName}”，但无法确认它一定由本次请求创建，且 Secret 无法再次取回。请先查看这条新增记录，再自行决定后续操作。`,
        });
      } else if (matches?.length === 0) {
        const oldMatch = refreshed?.some(
          (client) =>
            existingClientIds.includes(client.id) &&
            client.displayName === attempted.displayName &&
            client.redirectUri === attempted.redirectUri,
        );
        setCreateOutcomeChecked(true);
        setCreateError(
          oldMatch
            ? '核对列表中只有创建前已存在的同名同回调客户端，未发现本次提交后的新增匹配记录。不能据此判定创建成功；没有自动重试，请确认列表已更新后再由你决定是否再次提交。'
            : '已刷新完整客户端列表，未找到提交后新增的匹配记录。不能据此判定创建成功；没有自动重试，请确认列表已更新后再由你决定是否再次提交。',
        );
      } else if (matches && matches.length > 1) {
        setCreateOutcomeChecked(false);
        setCreateError(
          `发现 ${matches.length} 条提交后新增的匹配客户端，无法唯一确认本次请求对应哪条记录。Secret 无法再次取回；请逐条核对，不要据此停用旧客户端或自动重试。`,
        );
      }
    } finally {
      setCheckingCreateOutcome(false);
    }
  };

  const verifyEditOutcome = async () => {
    if (!editOutcomeUnknown) return;
    const { clientId, values } = editOutcomeUnknown;
    const refreshed = await load();
    const current = refreshed?.find((client) => client.id === clientId);
    if (!current) {
      setEditError(
        '已刷新列表，但未找到该客户端；操作结果仍未确认，请先刷新并定位客户端。',
      );
      return;
    }
    const matchesAttempt =
      current.displayName === values.displayName.trim() &&
      current.redirectUri === values.redirectUri &&
      current.scopes.slice().sort().join(' ') ===
        values.scopes.slice().sort().join(' ');
    if (matchesAttempt) {
      setEditOutcomeUnknown(undefined);
      setEditingClient(undefined);
      setEditError('');
      message.success('重新读取已确认配置已保存。');
      return;
    }
    const matchesOriginal =
      current.displayName === editingClient?.displayName &&
      current.redirectUri === editingClient?.redirectUri &&
      current.scopes.slice().sort().join(' ') ===
        editingClient?.scopes.slice().sort().join(' ');
    if (matchesOriginal) {
      setEditOutcomeChecked(true);
      setEditError(
        '已重新读取并确认配置仍为原值。没有自动重试；确认列表已更新后，可由你明确选择再次保存。',
      );
      return;
    }
    setEditOutcomeUnknown(undefined);
    setEditingClient(current);
    // 不确定结果与旧值、提交值均不一致时，表单必须回填最新服务端事实。
    editForm.setFieldsValue({
      displayName: current.displayName,
      redirectUri: current.redirectUri,
      scopes: current.scopes,
    });
    setEditError(
      '重新读取发现配置与本次提交和原值均不相同。表单已更新为服务端当前配置，请核对后再编辑；系统没有自动重试。',
    );
  };

  const saveClientConfiguration = async (
    values: OAuthClientConfigurationFields,
  ) => {
    if (!editingClient) return;
    const target = editingClient;
    const normalized: AppClientConfiguration = {
      displayName: values.displayName.trim(),
      redirectUri: values.redirectUri.trim(),
      scopes: values.scopes,
    };
    setSavingEdit(true);
    setEditError('');
    try {
      const updated = await updateOAuthClient(target.id, normalized);
      setItems((current) =>
        current.map((item) => (item.id === target.id ? updated : item)),
      );
      setSelectedClient(updated);
      setEditingClient(undefined);
      setEditOutcomeUnknown(undefined);
      setEditOutcomeChecked(false);
      message.success(
        normalized.redirectUri !== target.redirectUri ||
          normalized.scopes.slice().sort().join(' ') !==
            target.scopes.slice().sort().join(' ')
          ? '客户端配置已更新；原有授权已撤销，用户需要重新授权。'
          : '客户端配置已更新。',
      );
    } catch (error) {
      const normalizedError = normalizeApiError(error);
      if (normalizedError.status === 401) {
        resolveFailure(error);
        setEditingClient(undefined);
      } else if (normalizedError.status === 403) {
        setItems([]);
        setSelectedClient(undefined);
        setEditingClient(undefined);
        setLoadError(normalizedError.message);
      } else if (normalizedError.writeOutcomeUnknown) {
        setEditOutcomeUnknown({ clientId: target.id, values: normalized });
        setEditOutcomeChecked(false);
        setEditError(
          `${normalizedError.message} 请先刷新并核对客户端配置；系统不会自动重试。`,
        );
      } else {
        setEditError(applyAdminFormError(error, editForm));
      }
    } finally {
      setSavingEdit(false);
    }
  };

  const saveCreatedClient = async (values: CreateClientFields) => {
    setCreating(true);
    setCreateError('');
    let existingClientIds: string[];
    try {
      // Secret 只返回一次；先记录完整列表的 ID，避免把同名旧客户端误判为本次创建。
      const snapshot = await getOAuthClients();
      if (!isCompleteClientList(snapshot)) {
        throw new Error('客户端列表响应不完整');
      }
      existingClientIds = snapshot.map((client) => client.id);
    } catch {
      setCreateError(
        '提交前未能完整读取客户端列表，本次未发送创建请求。请刷新列表后再试。',
      );
      setCreating(false);
      return;
    }
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
        setCreateOutcomeUnknown({ values, existingClientIds });
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

  const changeClientStatus = async () => {
    if (!statusTarget) return;
    const { client: target, enabled } = statusTarget;
    setBusyClientId(target.id);
    setStatusError('');
    setStatusOutcomeUnknown(false);
    try {
      if (enabled) await enableOAuthClient(target.id);
      else await disableOAuthClient(target.id);
      setItems((current) =>
        current.map((item) =>
          item.id === target.id ? { ...item, enabled } : item,
        ),
      );
      setSelectedClient((current) =>
        current?.id === target.id ? { ...current, enabled } : current,
      );
      setStatusTarget(undefined);
      message.success(
        enabled
          ? '客户端已启用；原有授权不会恢复，用户需要重新授权。'
          : '客户端已停用，现有 App 授权已撤销。',
      );
    } catch (error) {
      const failure = resolveFailure(error);
      if (failure.accessLost) {
        setItems([]);
        setSelectedClient(undefined);
        setStatusTarget(undefined);
        setStatusOutcomeUnknown(false);
      }
      if (failure.redirectedToLogin) {
        setStatusTarget(undefined);
      } else {
        setStatusError(failure.message);
        if (normalizeApiError(error).writeOutcomeUnknown) {
          setStatusOutcomeUnknown(true);
          setStatusError(
            `${failure.message} 请重新读取客户端状态；系统不会自动重试。`,
          );
        }
      }
    } finally {
      setBusyClientId(undefined);
    }
  };

  const verifyStatusOutcome = async () => {
    if (!statusTarget) return;
    const target = statusTarget;
    setCheckingStatusOutcome(true);
    try {
      const refreshed = await load();
      const current = refreshed?.find(
        (client) => client.id === target.client.id,
      );
      if (current && current.enabled === target.enabled) {
        setStatusOutcomeUnknown(false);
        setStatusTarget(undefined);
        setStatusError('');
        message.success('重新读取已确认客户端状态已更新。');
      } else if (current) {
        setStatusOutcomeUnknown(false);
        setStatusTarget({ ...target, client: current });
        setStatusError(
          `重新读取已确认客户端仍处于${
            current.enabled ? '启用' : '停用'
          }状态；没有自动重试。若仍需${
            target.enabled ? '启用' : '停用'
          }，请再次明确点击按钮。`,
        );
      } else if (refreshed) {
        setStatusOutcomeUnknown(true);
        setStatusError(
          '已重新读取客户端列表，但未找到目标记录；操作结果仍未确认，请先刷新并定位该客户端。',
        );
      }
    } finally {
      setCheckingStatusOutcome(false);
    }
  };

  const rotateSecret = async () => {
    if (!rotateTarget) return;
    const target = rotateTarget;
    setRotatingSecret(true);
    setRotateError('');
    try {
      const result = await rotateOAuthClientSecret(target.id);
      setRotatedSecret(result);
      setRotateTarget(undefined);
      setRotateOutcomeUnknown(false);
      message.success('已生成新 Secret。请先保存，再关闭此窗口。');
      void load();
    } catch (error) {
      const failure = resolveFailure(error);
      if (failure.redirectedToLogin || failure.accessLost) {
        setRotateTarget(undefined);
        setRotateOutcomeUnknown(false);
      } else {
        setRotateError(
          normalizeApiError(error).writeOutcomeUnknown
            ? `${failure.message} 旧 Secret 可能已经失效；系统不会自动重试。若需恢复，请再次明确确认轮换并立即保存新值。`
            : failure.message,
        );
        setRotateOutcomeUnknown(normalizeApiError(error).writeOutcomeUnknown);
      }
    } finally {
      setRotatingSecret(false);
    }
  };

  const columns: ColumnsType<AppClient> = [
    {
      title: '应用',
      dataIndex: 'displayName',
      width: compactLayout ? '55%' : '18%',
      render: (value: string) => (
        <Typography.Text strong ellipsis>
          {value}
        </Typography.Text>
      ),
    },
    ...(!compactLayout
      ? [
          {
            title: 'Client ID',
            dataIndex: 'clientId',
            width: '20%',
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
            width: '23%',
            render: (value: string) => (
              <CopyableValue
                value={value}
                label="回调地址"
                onCopyError={reportCopyError}
                onCopySuccess={reportCopySuccess}
              />
            ),
          },
          {
            title: '允许申请的权限',
            dataIndex: 'scopes',
            width: '15%',
            render: (scopes: string[]) => {
              const visible = scopes.slice(0, 2);
              const remaining = scopes.slice(2);
              return (
                <Space wrap size={[4, 4]}>
                  {visible.map((scope) => (
                    <Tag key={scope}>{oauthScopeLabel(scope)}</Tag>
                  ))}
                  {remaining.length ? (
                    <Tooltip
                      title={remaining.map(oauthScopeLabel).join('、')}
                      placement="topLeft"
                    >
                      <Button
                        type="text"
                        size="small"
                        aria-label={`查看其他权限：${remaining
                          .map(oauthScopeLabel)
                          .join('、')}`}
                      >
                        +{remaining.length}
                      </Button>
                    </Tooltip>
                  ) : null}
                </Space>
              );
            },
          },
        ]
      : []),
    {
      title: '状态',
      dataIndex: 'enabled',
      width: compactLayout ? '20%' : '8%',
      render: (enabled: boolean) => (
        <Tag color={enabled ? 'green' : 'default'}>
          {enabled ? '已启用' : '已停用'}
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: compactLayout ? '25%' : '16%',
      render: (_, client) => (
        <Space size={compactLayout ? 0 : 8}>
          <Button
            type="link"
            size={compactLayout ? 'small' : 'middle'}
            onClick={() => {
              setCopyError('');
              setSelectedClient(client);
            }}
          >
            {compactLayout ? '查看' : '查看详情'}
          </Button>
          {!compactLayout ? (
            <Button
              type="link"
              danger={client.enabled}
              size="small"
              loading={busyClientId === client.id}
              disabled={Boolean(busyClientId)}
              onClick={() => {
                setStatusError('');
                setStatusOutcomeUnknown(false);
                setStatusTarget({ client, enabled: !client.enabled });
              }}
            >
              {client.enabled ? '停用' : '启用'}
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  const detail = selectedClient;

  return (
    <AdminPage
      title="OAuth 客户端"
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
              <Button onClick={() => void openNoticeClient()}>
                {writeNotice.actionLabel || '查看疑似客户端'}
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
        <div ref={tableContainerRef} style={{ width: '100%', minWidth: 0 }}>
          <Table<AppClient>
            rowKey="id"
            columns={columns}
            dataSource={pageRows}
            loading={loading}
            tableLayout="fixed"
            locale={{
              emptyText: loadError ? '列表读取失败' : '暂无 OAuth 客户端',
            }}
            pagination={false}
          />
        </div>
      ) : null}
      <ListPagination
        current={currentPage}
        pageSize={pageSize}
        total={items.length}
        loading={loading}
        onChange={(nextPage, nextSize) => {
          setPageSize(nextSize);
          setCurrentPage(nextPage);
        }}
      />

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
          <OAuthClientConfigFields />
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
        title="编辑 OAuth 客户端配置"
        open={Boolean(editingClient)}
        onCancel={() => {
          if (!savingEdit) {
            if (editOutcomeUnknown && editingClient) {
              setWriteNotice({
                type: 'warning',
                client: editingClient,
                actionLabel: '核对客户端配置',
                message: `「${editingClient.displayName}」的保存结果仍未确认。请重新读取配置后再决定是否继续，不要直接重复提交。`,
              });
            }
            setEditingClient(undefined);
            setEditOutcomeUnknown(undefined);
            setEditError('');
            setEditOutcomeChecked(false);
          }
        }}
        onOk={() => editForm.submit()}
        okText="保存配置"
        cancelText="取消"
        okButtonProps={{
          disabled: Boolean(editOutcomeUnknown),
        }}
        confirmLoading={savingEdit}
        closable={!savingEdit}
        keyboard={!savingEdit}
        maskClosable={!savingEdit}
        width="min(560px, calc(100vw - 24px))"
        destroyOnClose
      >
        {editingClient ? (
          <>
            <Typography.Paragraph type="secondary">
              Client ID
              保持不变。修改回调地址或权限后，现有用户授权会被撤销并需要重新授权。
            </Typography.Paragraph>
            <Form
              form={editForm}
              layout="vertical"
              onFinish={saveClientConfiguration}
              disabled={Boolean(editOutcomeUnknown)}
            >
              <OAuthClientConfigFields />
            </Form>
            {editError ? (
              <Alert
                role="alert"
                style={{ marginTop: 12 }}
                type="error"
                showIcon
                message={editError}
                action={
                  editOutcomeUnknown ? (
                    <Space wrap>
                      <Button
                        disabled={loading || savingEdit}
                        onClick={() => void verifyEditOutcome()}
                      >
                        刷新并核对配置
                      </Button>
                      {editOutcomeChecked ? (
                        <Button
                          onClick={() => {
                            setEditOutcomeUnknown(undefined);
                            setEditOutcomeChecked(false);
                            setEditError('');
                          }}
                        >
                          我已核对，仍要保存
                        </Button>
                      ) : null}
                    </Space>
                  ) : undefined
                }
              />
            ) : null}
          </>
        ) : null}
      </Modal>

      <Modal
        title={statusTarget?.enabled ? '启用客户端' : '停用客户端'}
        open={Boolean(statusTarget)}
        onCancel={() => {
          if (!busyClientId) {
            if (statusOutcomeUnknown && statusTarget) {
              setWriteNotice({
                type: 'warning',
                client: statusTarget.client,
                actionLabel: '核对客户端状态',
                message: `「${statusTarget.client.displayName}」的${
                  statusTarget.enabled ? '启用' : '停用'
                }结果仍未确认。请重新读取状态后再决定下一步，不要直接重复提交。`,
              });
            }
            setStatusTarget(undefined);
            setStatusError('');
            setStatusOutcomeUnknown(false);
          }
        }}
        onOk={() => void changeClientStatus()}
        okText={statusTarget?.enabled ? '确认启用' : '停用客户端'}
        cancelText="取消"
        okButtonProps={{
          danger: !statusTarget?.enabled,
          disabled:
            !statusTarget ||
            statusOutcomeUnknown ||
            busyClientId === statusTarget?.client.id,
        }}
        confirmLoading={Boolean(
          statusTarget && busyClientId === statusTarget.client.id,
        )}
        closable={!busyClientId}
        keyboard={!busyClientId}
        maskClosable={!busyClientId}
        destroyOnClose
      >
        {statusTarget ? (
          <>
            <Typography.Paragraph>
              确定{statusTarget.enabled ? '启用' : '停用'}{' '}
              <Typography.Text strong>
                {statusTarget.client.displayName}
              </Typography.Text>{' '}
              吗？
              {statusTarget.enabled
                ? '重新启用只允许新的授权流程，不会恢复已撤销的用户授权。'
                : '此操作会撤销该客户端当前所有用户授权，用户需要在恢复服务后重新授权。'}
            </Typography.Paragraph>
            {statusError ? (
              <Alert
                role="alert"
                type="error"
                showIcon
                message="客户端状态需要核对"
                description={statusError}
                action={
                  statusOutcomeUnknown ? (
                    <Button
                      disabled={checkingStatusOutcome || loading}
                      onClick={() => void verifyStatusOutcome()}
                    >
                      {checkingStatusOutcome ? '正在核对' : '重新读取状态'}
                    </Button>
                  ) : undefined
                }
              />
            ) : null}
          </>
        ) : null}
      </Modal>

      <Modal
        title="轮换 Client Secret"
        open={Boolean(rotateTarget)}
        onCancel={() => {
          if (!rotatingSecret) {
            if (rotateOutcomeUnknown && rotateTarget) {
              setWriteNotice({
                type: 'warning',
                client: rotateTarget,
                actionLabel: '重新轮换 Secret',
                action: 'retry-secret-rotation',
                message: `「${rotateTarget.displayName}」的 Secret 轮换结果仍未确认，旧 Secret 可能已失效。若要继续，请再次明确轮换并保存新值。`,
              });
            }
            setRotateTarget(undefined);
            setRotateError('');
            setRotateOutcomeUnknown(false);
          }
        }}
        onOk={() => void rotateSecret()}
        okText={rotateOutcomeUnknown ? '再次轮换并生成新 Secret' : '确认轮换'}
        cancelText="取消"
        okButtonProps={{ danger: true, disabled: rotatingSecret }}
        confirmLoading={rotatingSecret}
        closable={!rotatingSecret}
        keyboard={!rotatingSecret}
        maskClosable={!rotatingSecret}
        destroyOnClose
      >
        {rotateTarget ? (
          <>
            <Typography.Paragraph>
              为「{rotateTarget.displayName}」生成新 Secret。旧 Secret
              会立即失效，现有授权会被撤销；新值只显示一次，请准备好后再继续。
            </Typography.Paragraph>
            {rotateOutcomeUnknown ? (
              <Alert
                type="warning"
                showIcon
                message="上次轮换结果未确认"
                description="旧 Secret 可能已经失效。再次轮换会让上一次可能生成的新 Secret 也失效，并重新撤销授权。"
              />
            ) : null}
            {rotateError ? (
              <Alert
                role="alert"
                style={{ marginTop: 12 }}
                type="error"
                showIcon
                message={rotateError}
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
                    return <Tag key={scope}>{oauthScopeLabel(scope)}</Tag>;
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
            <Divider />
            <Space wrap>
              <Button
                type="primary"
                onClick={() => {
                  editForm.setFieldsValue({
                    displayName: detail.displayName,
                    redirectUri: detail.redirectUri,
                    scopes: detail.scopes,
                  });
                  setEditError('');
                  setEditOutcomeUnknown(undefined);
                  setEditOutcomeChecked(false);
                  setEditingClient(detail);
                }}
              >
                编辑配置
              </Button>
              <Button
                danger={detail.enabled}
                onClick={() => {
                  setStatusError('');
                  setStatusOutcomeUnknown(false);
                  setStatusTarget({ client: detail, enabled: !detail.enabled });
                }}
              >
                {detail.enabled ? '停用客户端' : '启用客户端'}
              </Button>
              <Button
                onClick={() => {
                  setRotateError('');
                  setRotateOutcomeUnknown(false);
                  setRotateTarget(detail);
                }}
              >
                轮换 Client Secret
              </Button>
            </Space>
          </>
        ) : null}
      </Drawer>

      <OneTimeValuesModal
        title={
          created ? '客户端凭证（仅此一次）' : '新 Client Secret（仅此一次）'
        }
        description={
          created
            ? '请立即将 Client Secret 保存到 Workbench 的受保护配置中。Client ID 也可在客户端详情中再次查看；Secret 不会再次返回。关闭后本页不会保留这两个值。'
            : '请立即将新 Secret 保存到受保护配置中。该 Secret 只显示一次；关闭后本页不会保留它。轮换已撤销客户端原有授权，使用者需要重新授权。'
        }
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
            : rotatedSecret
            ? [
                {
                  key: 'client-id',
                  label: 'Client ID',
                  value: rotatedSecret.clientId,
                },
                {
                  key: 'client-secret',
                  label: 'Client Secret',
                  value: rotatedSecret.clientSecret,
                  sensitive: true,
                },
              ]
            : []
        }
        open={Boolean(created || rotatedSecret)}
        onClose={() => {
          setCreated(undefined);
          setRotatedSecret(undefined);
        }}
      />
    </AdminPage>
  );
};

export default AdminOAuthClientsPage;
