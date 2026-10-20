import { DownOutlined, PlusOutlined, RightOutlined } from '@ant-design/icons';
import { Button, Dropdown, Row, Space } from 'antd';
import React from 'react';
import styles from './index.less';

/**
 * 通用表格操作组件
 * @param type 类型：'entity' 或 'relation'
 * @param itemLabel 实体/本体对象或关系的标签
 * @param exportDropdownOpen 导出下拉框是否打开
 * @param setExportDropdownOpen 设置导出下拉框是否打开
 * @param exportOptions 导出选项
 * @param onAdd 添加按钮点击回调
 * @param onImport 导入功能回调
 * @param readonly 是否只读模式
 * @param showImport 是否显示导入按钮
 * @param disableImport 是否禁用导入按钮
 */
const TableAction: React.FC<{
  type: 'entity' | 'relation';
  itemLabel: string;
  exportDropdownOpen: boolean;
  setExportDropdownOpen: (open: boolean) => void;
  exportOptions: { key: string; label: string; onClick?: () => void }[];
  onAdd?: () => void;
  onImport?: () => void;
  readonly?: boolean;
  showImport?: boolean;
  disableImport?: boolean;
}> = ({
  type,
  itemLabel,
  exportDropdownOpen,
  setExportDropdownOpen,
  exportOptions,
  onAdd,
  onImport,
  readonly = false,
  showImport = true,
  disableImport = false,
}) => {
  /** 处理添加按钮点击 */
  const handleAddClick = () => {
    if (onAdd) {
      onAdd();
    }
  };

  /** 处理导入按钮点击 */
  const handleImport = () => {
    if (onImport) {
      onImport();
    }
  };

  /** 计算导入按钮是否禁用 */
  const importDisabled = readonly || disableImport;

  return (
    <Row className={styles.tableActions}>
      <Space>
        <Button
          icon={<PlusOutlined />}
          type="link"
          size="small"
          onClick={handleAddClick}
          disabled={readonly}
        >
          {`新建${itemLabel}`}
        </Button>
        {/* {showImport && (
          <Button
            type="link"
            size="small"
            disabled={importDisabled}
            onClick={handleImport}
          >
            导入
          </Button>
        )}
        <Dropdown
          menu={{
            items: exportOptions.map((item) => ({
              key: item.key,
              label: item.label,
              onClick: item.onClick,
            })),
          }}
          onOpenChange={setExportDropdownOpen}
        >
          <Button type="link" size="small">
            导出 {exportDropdownOpen ? <DownOutlined /> : <RightOutlined />}
          </Button>
        </Dropdown> */}
      </Space>
    </Row>
  );
};

export default TableAction;
