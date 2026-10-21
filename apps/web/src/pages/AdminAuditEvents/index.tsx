import AdminPage from '@/features/admin/AdminPage';
import {
  AUDIT_EVENT_OPTIONS,
  presentAuditActor,
  presentAuditEvent,
} from '@/features/admin/auditPresentation';
import {
  ADMIN_TIME_ZONE_LABEL,
  formatAdminDateTime,
} from '@/features/admin/dateTime';
import {
  getAuditEvents,
  getUsers,
  type AuditEvent,
  type AuditEventFilters,
  type ManagedUser,
} from '@/features/admin/service';
import { useAdminRequestError } from '@/features/admin/useAdminRequestError';
import { useCurrentUser } from '@/features/auth/context';
import FilterBar from '@/features/lists/FilterBar';
import ListPagination from '@/features/lists/ListPagination';
import {
  Alert,
  Button,
  Collapse,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Result,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React, { useEffect, useRef, useState } from 'react';
import styles from './index.less';

type DraftFilters = {
  start: string;
  end: string;
  eventTypes: string[];
  actorUserId: string;
};

const EMPTY_DRAFT: DraftFilters = {
  start: '',
  end: '',
  eventTypes: [],
  actorUserId: '',
};
const DEFAULT_PAGE_SIZE = 20;

function stringDetail(
  details: Record<string, unknown>,
  field: string,
): string | undefined {
  const value = details[field];
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function beijingDateTimeLocal(date: Date): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date);
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((item) => item.type === type)?.value || '';
  return `${part('year')}-${part('month')}-${part('day')}T${part(
    'hour',
  )}:${part('minute')}`;
}

function asBeijingOffset(
  value: string,
  endOfMinute = false,
): string | undefined {
  if (!value) return undefined;
  // datetime-local 是不带时区的墙上时间；界面明确按北京时间解释并提交 UTC+08:00。
  return `${value}:${endOfMinute ? '59.999' : '00'}+08:00`;
}

function toQueryFilters(draft: DraftFilters): AuditEventFilters {
  return {
    startTime: asBeijingOffset(draft.start),
    endTime: asBeijingOffset(draft.end, true),
    eventTypes: draft.eventTypes.length ? draft.eventTypes : undefined,
    actorUserId: draft.actorUserId.trim() || undefined,
  };
}

function hasFilters(filters: AuditEventFilters): boolean {
  return Boolean(
    filters.startTime ||
      filters.endTime ||
      filters.eventTypes?.length ||
      filters.actorUserId,
  );
}

const AdminAuditEventsPage: React.FC = () => {
  const actor = useCurrentUser();
  const resolveFailure = useAdminRequestError();
  const [rows, setRows] = useState<AuditEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [hasLoaded, setHasLoaded] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [cursorStack, setCursorStack] = useState<Array<number | undefined>>([
    undefined,
  ]);
  const [loadError, setLoadError] = useState('');
  const [selectedEvent, setSelectedEvent] = useState<AuditEvent>();
  const [draft, setDraft] = useState<DraftFilters>(EMPTY_DRAFT);
  const [filterError, setFilterError] = useState('');
  const [actorUsers, setActorUsers] = useState<ManagedUser[]>([]);
  const [actorUsersTotal, setActorUsersTotal] = useState(0);
  const [actorUsersPage, setActorUsersPage] = useState(0);
  const [actorUsersLoading, setActorUsersLoading] = useState(false);
  const [actorSearchText, setActorSearchText] = useState('');
  const [selectedActor, setSelectedActor] = useState<ManagedUser>();
  const [actorLoadError, setActorLoadError] = useState('');
  const [actorRefresh, setActorRefresh] = useState(0);
  const [quickRange, setQuickRange] = useState('custom');
  const [appliedFilters, setAppliedFilters] = useState<AuditEventFilters>({});
  const [filtersDirty, setFiltersDirty] = useState(false);
  const requestSequence = useRef(0);
  const actorRequestSequence = useRef(0);
  const actorLoading = useRef(false);

  const loadActorUsers = async (
    keyword: string,
    page: number,
    append: boolean,
  ) => {
    if (actorLoading.current) return;
    const sequence = ++actorRequestSequence.current;
    actorLoading.current = true;
    setActorUsersLoading(true);
    try {
      const result = await getUsers(page, 20, keyword);
      if (sequence !== actorRequestSequence.current) return;
      const nextUsers = result.items || [];
      setActorUsers((current) =>
        append
          ? [
              ...current,
              ...nextUsers.filter(
                (user) => !current.some((item) => item.id === user.id),
              ),
            ]
          : nextUsers,
      );
      setActorUsersTotal(result.total || 0);
      setActorUsersPage(page);
      setActorLoadError('');
    } catch (error) {
      if (sequence !== actorRequestSequence.current) return;
      const failure = resolveFailure(error);
      setActorLoadError(failure.message || '用户列表加载失败');
      if (!append) {
        setActorUsers([]);
        setActorUsersTotal(0);
      }
    } finally {
      if (sequence === actorRequestSequence.current) {
        actorLoading.current = false;
        setActorUsersLoading(false);
      }
    }
  };

  const load = async (
    cursor: number | undefined,
    nextStack: Array<number | undefined>,
    filters: AuditEventFilters = appliedFilters,
    size = pageSize,
  ) => {
    const sequence = ++requestSequence.current;
    setLoading(true);
    setLoadError('');
    try {
      // 多取一条只用于判断是否还有下一页，不把它显示给用户。
      const values = await getAuditEvents(size + 1, cursor, filters);
      if (sequence !== requestSequence.current) return;
      setRows(values.slice(0, size));
      setHasMore(values.length > size);
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
    if (actor.systemAdmin)
      void load(undefined, [undefined], {}, DEFAULT_PAGE_SIZE);
    return () => {
      requestSequence.current += 1;
    };
  }, [actor.systemAdmin]);

  useEffect(() => {
    if (!actor.systemAdmin) return;
    const timer = window.setTimeout(
      () => {
        actorLoading.current = false;
        void loadActorUsers(actorSearchText, 0, false);
      },
      actorSearchText ? 250 : 0,
    );
    return () => {
      window.clearTimeout(timer);
      actorRequestSequence.current += 1;
      actorLoading.current = false;
    };
  }, [actor.systemAdmin, actorSearchText, actorRefresh]);

  if (!actor.systemAdmin) {
    return (
      <AdminPage title="安全审计">
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
  const query = () => {
    if (draft.start && draft.end && draft.start > draft.end) {
      setFilterError('开始时间不能晚于结束时间。');
      return;
    }
    setFilterError('');
    const filters = toQueryFilters(draft);
    setAppliedFilters(filters);
    setFiltersDirty(false);
    void load(undefined, [undefined], filters);
  };
  const reset = () => {
    setFilterError('');
    setDraft(EMPTY_DRAFT);
    setSelectedActor(undefined);
    setActorSearchText('');
    setActorLoadError('');
    setActorRefresh((value) => value + 1);
    setAppliedFilters({});
    setFiltersDirty(false);
    void load(undefined, [undefined], {});
  };
  const refreshCurrent = () => {
    if (filtersDirty) return;
    void load(currentCursor, cursorStack, appliedFilters);
  };
  const viewLatest = () => {
    setFiltersDirty(false);
    void load(undefined, [undefined], appliedFilters);
  };
  const changeDraft = (next: DraftFilters) => {
    setFilterError('');
    setDraft(next);
    setQuickRange('custom');
    setFiltersDirty(true);
    // 新筛选不允许继续使用旧游标；列表更新须由“查询”明确触发。
    setCursorStack([undefined]);
  };
  const applyQuickRange = (value: string) => {
    if (value === 'custom') {
      setQuickRange(value);
      return;
    }
    if (value === 'all') {
      changeDraft({ ...draft, start: '', end: '' });
      setQuickRange(value);
      return;
    }
    const now = new Date();
    const start = new Date(now);
    if (value === 'hour') start.setTime(now.getTime() - 60 * 60 * 1000);
    if (value === 'day') start.setTime(now.getTime() - 24 * 60 * 60 * 1000);
    if (value === 'week')
      start.setTime(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    changeDraft({
      ...draft,
      start: value ? beijingDateTimeLocal(start) : '',
      end: value ? beijingDateTimeLocal(now) : '',
    });
    setQuickRange(value);
  };

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
        return <Typography.Text>{presentation.label}</Typography.Text>;
      },
    },
    {
      title: '操作身份',
      dataIndex: 'actorUserId',
      width: 190,
      render: (_, event) => presentAuditActor(event),
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

  const showTable = hasLoaded || loading;
  const detailPresentation = selectedEvent
    ? presentAuditEvent(selectedEvent)
    : undefined;
  const details = selectedEvent?.details || {};
  const actorOptions = [
    ...(selectedActor &&
    !actorUsers.some((user) => user.id === selectedActor.id)
      ? [selectedActor]
      : []),
    ...actorUsers,
  ];
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
    <AdminPage title="安全审计">
      <FilterBar>
        <Space
          className={styles.filterControls}
          wrap
          align="end"
          style={{ width: '100%' }}
        >
          <label className={styles.filterField}>
            <Typography.Text type="secondary">快捷时间</Typography.Text>
            <Select
              aria-label="快捷时间范围"
              value={quickRange}
              style={{ width: 150 }}
              options={[
                { value: 'custom', label: '自定义范围' },
                { value: 'hour', label: '最近 1 小时' },
                { value: 'day', label: '最近 24 小时' },
                { value: 'week', label: '最近 7 天' },
                { value: 'all', label: '全部时间' },
              ]}
              onChange={applyQuickRange}
            />
          </label>
          <label className={styles.filterField}>
            <Typography.Text type="secondary">
              开始时间（北京时间）
            </Typography.Text>
            <Input
              aria-label="开始时间（北京时间）"
              type="datetime-local"
              value={draft.start}
              status={filterError ? 'error' : undefined}
              aria-describedby={
                filterError ? 'audit-time-filter-error' : undefined
              }
              style={{ width: '100%' }}
              onChange={(event) =>
                changeDraft({ ...draft, start: event.target.value })
              }
            />
          </label>
          <label className={styles.filterField}>
            <Typography.Text type="secondary">
              结束时间（北京时间）
            </Typography.Text>
            <Input
              aria-label="结束时间（北京时间）"
              type="datetime-local"
              value={draft.end}
              status={filterError ? 'error' : undefined}
              aria-describedby={
                filterError ? 'audit-time-filter-error' : undefined
              }
              style={{ width: '100%' }}
              onChange={(event) =>
                changeDraft({ ...draft, end: event.target.value })
              }
            />
          </label>
          {filterError ? (
            <Typography.Text
              id="audit-time-filter-error"
              role="alert"
              type="danger"
            >
              {filterError}
            </Typography.Text>
          ) : null}
          <label className={`${styles.filterField} ${styles.eventField}`}>
            <Typography.Text type="secondary">事件类型</Typography.Text>
            <Select
              aria-label="事件类型"
              mode="multiple"
              allowClear
              maxTagCount="responsive"
              style={{ width: '100%' }}
              placeholder="全部事件"
              value={draft.eventTypes}
              options={AUDIT_EVENT_OPTIONS}
              onChange={(eventTypes) => changeDraft({ ...draft, eventTypes })}
            />
          </label>
          <div className={`${styles.filterField} ${styles.actorField}`}>
            <Typography.Text type="secondary">操作人</Typography.Text>
            <Select
              aria-label="按姓名或用户名筛选操作人"
              showSearch
              allowClear
              filterOption={false}
              placeholder="搜索姓名或用户名"
              value={selectedActor?.username || undefined}
              options={actorOptions.map((user) => ({
                // Ant Design 会生成辅助技术使用的 option 回退列表，选项值使用用户名而非内部 ID。
                value: user.username,
                label: user.displayName
                  ? `${user.displayName}（@${user.username}）`
                  : `@${user.username}`,
              }))}
              loading={actorUsersLoading}
              style={{ width: '100%' }}
              onSearch={(value) => {
                actorRequestSequence.current += 1;
                actorLoading.current = false;
                setActorSearchText(value);
                setActorUsers([]);
                setActorUsersTotal(0);
                setActorLoadError('');
              }}
              onChange={(value) => {
                setSelectedActor(
                  actorOptions.find((user) => user.username === value),
                );
                changeDraft({
                  ...draft,
                  actorUserId:
                    actorOptions.find((user) => user.username === value)?.id ||
                    '',
                });
                setActorSearchText('');
              }}
              onPopupScroll={(event) => {
                const element = event.target as HTMLDivElement;
                if (
                  element.scrollTop + element.clientHeight >=
                    element.scrollHeight - 24 &&
                  actorUsers.length < actorUsersTotal &&
                  !actorUsersLoading
                ) {
                  void loadActorUsers(
                    actorSearchText,
                    actorUsersPage + 1,
                    true,
                  );
                }
              }}
              notFoundContent={
                actorUsersLoading
                  ? '正在查找账号…'
                  : actorSearchText.trim()
                  ? '未找到匹配账号'
                  : '暂无用户账号'
              }
            />
          </div>
          {actorLoadError ? (
            <Alert
              type="error"
              showIcon
              message="用户列表加载失败"
              description={actorLoadError}
              action={
                <Button
                  disabled={actorUsersLoading}
                  onClick={() => void loadActorUsers(actorSearchText, 0, false)}
                >
                  重试
                </Button>
              }
              style={{ marginTop: 8 }}
            />
          ) : null}
          <Space wrap>
            <Button type="primary" loading={loading} onClick={query}>
              查询
            </Button>
            <Button disabled={loading} onClick={reset}>
              重置
            </Button>
            <Button disabled={loading || filtersDirty} onClick={refreshCurrent}>
              刷新
            </Button>
          </Space>
        </Space>
      </FilterBar>

      {loadError ? (
        <Alert
          style={{ marginBottom: 16 }}
          type="error"
          showIcon
          message={
            rows.length
              ? '审计记录未更新，仍显示上次读取的数据。'
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
          locale={{
            emptyText: (
              <Empty
                description={
                  loadError
                    ? '列表读取失败'
                    : hasFilters(appliedFilters)
                    ? '筛选条件下没有审计记录'
                    : '暂无安全审计记录'
                }
              />
            ),
          }}
          pagination={false}
          scroll={{ x: 1120 }}
        />
      ) : null}
      {hasLoaded && !loadError ? (
        <ListPagination
          mode="cursor"
          pageSize={pageSize}
          loading={loading || filtersDirty}
          canGoNewer={cursorStack.length > 1 && rows.length > 0}
          canGoOlder={hasMore && rows.length > 0}
          onNewer={previousPage}
          onOlder={nextPage}
          onPageSizeChange={(size) => {
            setPageSize(size);
            void load(undefined, [undefined], appliedFilters, size);
          }}
        />
      ) : null}

      {cursorStack.length > 1 ? (
        <div className={styles.viewLatest}>
          <Button disabled={loading || filtersDirty} onClick={viewLatest}>
            查看最新
          </Button>
        </div>
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
                {presentAuditActor(selectedEvent)}
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
