import { importGraph } from '@/services_v1/graphService';
import { UploadOutlined } from '@ant-design/icons';
import { App, Button, Col, Form, Modal, Row, Typography, Upload } from 'antd';
import type {
  RcFile,
  UploadChangeParam,
  UploadFile,
} from 'antd/es/upload/interface';
import { FC, useState } from 'react';
import styles from './index.less';

/** 导入图谱弹窗 */
const ImportGraphPop: FC<{
  isVisible: boolean;
  onCancel: () => void;
  onSubmit: (values?: any) => void;
  title?: string;
  uploadAction?: string;
  organizationId?: string;
  graphType: 'ONTOLOGY' | 'ENTITY';
}> = ({
  isVisible,
  onCancel,
  onSubmit,
  title = '导入图谱',
  organizationId,
  graphType,
}) => {
  const [form] = Form.useForm();
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [uploading, setUploading] = useState(false);
  const { message } = App.useApp();

  const handleCancel = () => {
    form.resetFields();
    setFileList([]);
    onCancel();
  };

  /** 导入图谱 */
  const handleSubmit = async () => {
    if (fileList.length === 0) {
      message.error('请先选择要上传的文件');
      return;
    }
    setUploading(true);
    try {
      const file = fileList[0].originFileObj as RcFile;
      if (!file) {
        message.error('文件对象无效');
        setUploading(false);
        return;
      }
      const response = await importGraph(
        {
          file: file as any,
          organizationId,
          graphType,
        },
        { skipErrorHandler: false },
      );
      if (response.success) {
        message.success(
          `${graphType === 'ONTOLOGY' ? '本体' : '实体'}图谱导入成功`,
        );
        handleCancel();
        onSubmit();
      }
    } finally {
      setUploading(false);
    }
  };

  /** 文件上传配置 */
  const uploadProps = {
    name: 'file',
    fileList,
    multiple: false,
    accept: '.json',
    onRemove: () => {
      setFileList([]);
    },
    beforeUpload: (file: RcFile) => {
      /** 验证文件类型 */
      const isJson =
        file.type === 'application/json' || file.name.endsWith('.json');
      if (!isJson) {
        message.error('只能上传JSON文件!');
        return Upload.LIST_IGNORE;
      }
      /** 限制文件大小（100MB） */
      const isLt10M = file.size / 1024 / 1024 < 100;
      if (!isLt10M) {
        message.error('文件大小不能超过100MB!');
        return Upload.LIST_IGNORE;
      }
      setFileList([file as UploadFile]);
      /** 阻止自动上传，改为手动上传 */
      return false;
    },
    onChange(info: UploadChangeParam<UploadFile>) {
      /** 更新文件列表 */
      const { fileList: newFileList } = info;
      setFileList(newFileList);
    },
  };

  return (
    <Modal
      title={title}
      open={isVisible}
      onCancel={handleCancel}
      footer={[
        <Button key="cancel" onClick={handleCancel}>
          取消
        </Button>,
        <Button
          key="submit"
          type="primary"
          onClick={handleSubmit}
          loading={uploading}
          disabled={fileList.length === 0}
        >
          导入
        </Button>,
      ]}
    >
      <Form form={form} name="importGraphForm">
        <Row className={styles.importContainer}>
          <Col span={24}>
            <Upload.Dragger {...uploadProps} className={styles.uploadArea}>
              <Typography.Text className={styles.uploadDragIcon}>
                <UploadOutlined />
              </Typography.Text>
              <Typography.Paragraph className={styles.uploadHint}>
                点击或拖拽JSON文件到此区域上传
              </Typography.Paragraph>
              <Typography.Paragraph
                className={styles.uploadHint}
                type="secondary"
              >
                支持的文件格式: .json
              </Typography.Paragraph>
            </Upload.Dragger>
            {/* {downloadTemplateUrl && downloadTemplateUrl !== '#' && (
              <Row className={styles.uploadTip}>
                <Typography.Link
                  href={downloadTemplateUrl}
                  className={styles.downloadTemplate}
                >
                  下载导入模板
                </Typography.Link>
              </Row>
            )} */}
          </Col>
        </Row>
      </Form>
    </Modal>
  );
};

export default ImportGraphPop;
