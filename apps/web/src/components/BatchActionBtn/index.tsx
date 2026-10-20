import { DownOutlined } from '@ant-design/icons';
import { Button, Dropdown, message } from 'antd';
import { FC } from 'react';

/**
 * 批量操作按钮
 * @param activeTab 当前激活的标签页
 * @param selectedRowKeys 选中的行键值
 * @param onBatchDelete 批量删除回调
 */
const BatchActionBtn: FC<{
  activeTab?: 'entity' | 'relation';
  selectedRowKeys?: React.Key[];
  onBatchDelete?: (keys: React.Key[]) => void;
}> = ({
  activeTab = 'entity',
  selectedRowKeys = [],
  onBatchDelete,
}) => {
  const batchOptions = [
    {
      key: 'delete',
      label: '删除',
    },
  ];

  const handleMenuClick = ({ key }: { key: string }) => {
    if (key === 'delete') {
      if (selectedRowKeys.length === 0) {
        message.warning('请先选择需要删除的项');
        return;
      }

      if (onBatchDelete) {
        onBatchDelete(selectedRowKeys);
      } else {
        console.log(
          `批量删除${activeTab === 'entity' ? '对象' : '关系'}:`,
          selectedRowKeys,
        );
        message.success(
          `已删除${selectedRowKeys.length}个${
            activeTab === 'entity' ? '对象' : '关系'
          }`,
        );
      }
    }
  };

  return (
    <Dropdown
      menu={{
        items: batchOptions.map((item) => ({
          key: item.key,
          label: item.label,
        })),
        onClick: handleMenuClick,
      }}
      disabled={selectedRowKeys.length === 0}
    >
      <Button size="small">
        批量操作 <DownOutlined />
      </Button>
    </Dropdown>
  );
};

export default BatchActionBtn;
