import { LeftOutlined, RightOutlined } from '@ant-design/icons';
import { Button, Grid, Pagination, Select, Space, Typography } from 'antd';
import React from 'react';
import styles from './ListPagination.less';

export const LIST_PAGE_SIZES = [20, 50, 100];

export type PagePaginationProps = {
  mode?: 'page';
  current: number;
  pageSize: number;
  total: number;
  loading?: boolean;
  onChange: (page: number, pageSize: number) => void;
};

export type CursorPaginationProps = {
  mode: 'cursor';
  pageSize: number;
  loading?: boolean;
  canGoNewer: boolean;
  canGoOlder: boolean;
  onNewer: () => void;
  onOlder: () => void;
  onPageSizeChange: (pageSize: number) => void;
};

export type ListPaginationProps = PagePaginationProps | CursorPaginationProps;

const sizeOptions = LIST_PAGE_SIZES.map((value) => ({
  value,
  label: value + ' 条/页',
}));

/** 只统一分页呈现；数据加载、权限和列表状态仍由各业务页面负责。 */
const ListPagination: React.FC<ListPaginationProps> = (props) => {
  const screens = Grid.useBreakpoint();
  const compact = !screens.md;

  if (props.mode === 'cursor') {
    return (
      <div className={styles.pagination} aria-label="审计记录分页">
        <Space size={8} wrap>
          <Button
            size="small"
            icon={<LeftOutlined aria-hidden="true" />}
            disabled={!props.canGoNewer || props.loading}
            onClick={props.onNewer}
          >
            较新记录
          </Button>
          <Button
            size="small"
            icon={<RightOutlined aria-hidden="true" />}
            iconPosition="end"
            disabled={!props.canGoOlder || props.loading}
            onClick={props.onOlder}
          >
            更早记录
          </Button>
          <Select
            aria-label="每页条数"
            size="small"
            value={props.pageSize}
            options={sizeOptions}
            disabled={props.loading}
            onChange={props.onPageSizeChange}
            style={{ width: 112 }}
          />
        </Space>
      </div>
    );
  }

  const pageCount = Math.max(1, Math.ceil(props.total / props.pageSize));
  const visibleTotal = Math.max(props.total, 1);

  return (
    <div className={styles.pagination} aria-label="列表分页">
      {compact ? (
        <Space size={8}>
          <Button
            size="small"
            aria-label="上一页"
            icon={<LeftOutlined aria-hidden="true" />}
            disabled={props.current <= 1 || props.loading}
            onClick={() => props.onChange(props.current - 1, props.pageSize)}
          />
          <Typography.Text aria-live="polite">
            {props.current} / {pageCount}
          </Typography.Text>
          <Button
            size="small"
            aria-label="下一页"
            icon={<RightOutlined aria-hidden="true" />}
            disabled={props.current >= pageCount || props.loading}
            onClick={() => props.onChange(props.current + 1, props.pageSize)}
          />
        </Space>
      ) : (
        <Pagination
          size="small"
          current={props.current}
          pageSize={props.pageSize}
          total={visibleTotal}
          hideOnSinglePage={false}
          showSizeChanger={false}
          showTotal={() => '共 ' + props.total + ' 条'}
          disabled={props.loading}
          onChange={(page) => props.onChange(page, props.pageSize)}
        />
      )}
      <Select
        aria-label="每页条数"
        size="small"
        value={props.pageSize}
        options={sizeOptions}
        disabled={props.loading}
        onChange={(nextSize) => props.onChange(1, nextSize)}
        style={{ width: 112 }}
      />
    </div>
  );
};

export default ListPagination;
