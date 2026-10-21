import { PropertySchema } from '@/services_v1/typings';
import { getPlaceholderByType, validatePropertyValue } from '@/utils/validator';
import ListPagination from '@/features/lists/ListPagination';
import { Checkbox, Input, Select, Table, Tooltip } from 'antd';
import React, { useEffect, useState } from 'react';

/**
 * 属性表格
 * @param properties 属性模板列表（PropertySchema）
 * @param propertyJson 属性值对象（可选，用于实体对象）
 * @param setPropertyJson 设置属性值对象（可选，用于实体对象）
 * @param setProperties 设置属性列表（可选，用于本体对象）
 * @param loading 是否加载中
 * @param showDescription 是否显示属性描述列，默认为false
 * @param onPropertyChange 属性值变化时的回调函数
 */
const PropertyTable: React.FC<{
  properties: PropertySchema[];
  propertyJson?: Record<string, any>;
  setPropertyJson?: (propertyJson: Record<string, any>) => void;
  setProperties?: (properties: any[]) => void;
  loading?: boolean;
  showDescription?: boolean;
  onPropertyChange?: (propertyName: string, value: string) => void;
}> = ({
  properties,
  propertyJson,
  setPropertyJson,
  setProperties,
  loading = false,
  showDescription = false,
  onPropertyChange,
}) => {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const pageRows = properties.slice((page - 1) * pageSize, page * pageSize);

  useEffect(() => {
    const lastPage = Math.max(1, Math.ceil(properties.length / pageSize));
    if (page > lastPage) setPage(lastPage);
  }, [page, pageSize, properties.length]);

  /** 获取属性的当前值 对于 OBJECT 类型，如果值是对象则序列化为 JSON 字符串 */
  const getPropertyValue = (record: PropertySchema): any => {
    let value: any;
    if (propertyJson && record.name) {
      value = propertyJson[record.name];
    } else {
      value = (record as any)?.value ?? '';
    }
    // 如果是 OBJECT 类型且值是对象（非字符串），则序列化为 JSON 字符串
    if (record.type === 'OBJECT' && value && typeof value === 'object') {
      try {
        return JSON.stringify(value, null, 2);
      } catch (e) {
        console.error('Failed to stringify object:', e);
        return '';
      }
    }

    return value;
  };

  /** 判断属性值是否为空 */
  const isValueEmpty = (value: any): boolean => {
    return value === '' || value === undefined || value === null;
  };

  /** 处理属性值变化 */
  const handlePropertyValueChange = (propertyName: string, value: any) => {
    // 更新属性值
    if (setPropertyJson) {
      setPropertyJson({ ...propertyJson, [propertyName]: value });
    } else if (setProperties) {
      const newProperties = [...properties] as any[];
      const property = newProperties.find((p) => p.name === propertyName);
      if (property) {
        property.value = value;
        setProperties(newProperties);
      }
    }
    // 特殊属性回调（name/description 需要同步到表单）
    if (
      onPropertyChange &&
      (propertyName === 'name' || propertyName === 'description')
    ) {
      onPropertyChange(propertyName, value || '');
    }
  };

  const columns = [
    {
      title: '属性名称',
      dataIndex: 'name',
      key: 'name',
      width: 140,
      render: (text: string) => (
        <Input value={text} placeholder="请输入属性名称" disabled={true} />
      ),
    },
    {
      title: '属性类型',
      dataIndex: 'type',
      key: 'type',
      width: 120,
      render: (text: string) => (
        <Select
          value={text}
          style={{ width: '100%' }}
          options={[
            { label: '字符串', value: 'STRING' },
            { label: '数字', value: 'NUMBER' },
            { label: '布尔', value: 'BOOLEAN' },
            { label: '对象', value: 'OBJECT' },
          ]}
          disabled={true}
        />
      ),
    },
    {
      title: '必填',
      dataIndex: 'required',
      key: 'required',
      width: 80,
      render: (text: string | boolean) => (
        <Checkbox
          checked={text === 'REQUIRED' || text === 'true' || text === true}
          disabled={true}
        />
      ),
    },
    /** 根据showDescription决定是否显示描述列 */
    ...(showDescription
      ? [
          {
            title: '属性描述',
            dataIndex: 'description',
            key: 'description',
            width: 150,
            render: (text: string) => <Input value={text} disabled={true} />,
          },
        ]
      : []),
    {
      title: '属性值',
      dataIndex: 'value',
      key: 'value',
      width: 160,
      render: (_: string, record: PropertySchema) => {
        const text = getPropertyValue(record);
        const isRequired = record.required === 'REQUIRED';

        if (record.type === 'BOOLEAN') {
          return (
            <Select
              value={!isValueEmpty(text) ? String(text) : undefined}
              placeholder="请选择true或false"
              style={{ width: '100%' }}
              options={[
                { label: 'true', value: 'true' },
                { label: 'false', value: 'false' },
              ]}
              onChange={(value) =>
                record.name && handlePropertyValueChange(record.name, value)
              }
              status={isRequired && isValueEmpty(text) ? 'error' : ''}
            />
          );
        }

        if (record.type === 'NUMBER') {
          const hasError =
            !isValueEmpty(text) &&
            !validatePropertyValue(String(text), record.type);
          return (
            <Tooltip
              placement="topLeft"
              title={hasError ? '请输入有效的数字' : ''}
              color="red"
            >
              <div>
                <Input
                  value={text}
                  placeholder="请输入数字"
                  style={{ width: '100%' }}
                  onChange={(e) => {
                    record.name &&
                      handlePropertyValueChange(record.name, e.target.value);
                  }}
                  onBlur={(e) => {
                    // 失去焦点时自动去除首尾空格
                    const trimmedValue = e.target.value.trim();
                    if (trimmedValue !== e.target.value) {
                      record.name &&
                        handlePropertyValueChange(record.name, trimmedValue);
                    }
                  }}
                  status={
                    (isRequired && isValueEmpty(text)) || hasError
                      ? 'error'
                      : ''
                  }
                />
              </div>
            </Tooltip>
          );
        }

        if (record.type === 'OBJECT') {
          const hasError = text && !validatePropertyValue(text, record.type);
          return (
            <Tooltip
              placement="topLeft"
              title={hasError ? 'JSON格式不正确，请检查格式' : ''}
              color="red"
            >
              <div>
                <Input.TextArea
                  value={text}
                  placeholder={
                    record.type ? getPlaceholderByType(record.type) : ''
                  }
                  onChange={(e) => {
                    record.name &&
                      handlePropertyValueChange(record.name, e.target.value);
                  }}
                  onBlur={(e) => {
                    // 失去焦点时自动去除首尾空格
                    const trimmedValue = e.target.value.trim();
                    if (trimmedValue !== e.target.value) {
                      record.name &&
                        handlePropertyValueChange(record.name, trimmedValue);
                    }
                  }}
                  status={
                    (isRequired && isValueEmpty(text)) || hasError
                      ? 'error'
                      : ''
                  }
                  rows={3}
                />
              </div>
            </Tooltip>
          );
        }

        // STRING 类型
        return (
          <Input
            value={text}
            placeholder={record.type ? getPlaceholderByType(record.type) : ''}
            onChange={(e) => {
              record.name &&
                handlePropertyValueChange(record.name, e.target.value);
            }}
            onBlur={(e) => {
              // 失去焦点时自动去除首尾空格
              const trimmedValue = e.target.value.trim();
              if (trimmedValue !== e.target.value) {
                record.name &&
                  handlePropertyValueChange(record.name, trimmedValue);
              }
            }}
            status={isRequired && isValueEmpty(text) ? 'error' : ''}
          />
        );
      },
    },
    // {
    //   title: '属性元数据/Metadata/标签',
    //   dataIndex: 'value',
    //   key: 'value',
    //   width: 320,
    //   render: (text: string, record: any, index: number) => (
    //     <Input value={text} placeholder="请输入属性元数据" />
    //   ),
    // },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0, width: '100%' }}>
      <Table
        rowKey={(record) => record.name || `property-${properties.indexOf(record)}`}
        dataSource={pageRows}
        pagination={false}
        size="small"
        bordered
        loading={loading}
        columns={columns}
        scroll={{ x: 'max-content' }}
      />
      <ListPagination
        current={page}
        pageSize={pageSize}
        total={properties.length}
        loading={loading}
        onChange={(nextPage, nextSize) => {
          setPage(nextPage);
          setPageSize(nextSize);
        }}
      />
    </div>
  );
};

export default PropertyTable;
