import Loading from '@/loading';
import { DeleteOutlined, FileOutlined, InboxOutlined } from '@ant-design/icons';
import { Alert, App, Button, Modal, Row, Typography, Upload } from 'antd';
import Dragger from 'antd/es/upload/Dragger';
import { useState } from 'react';
import styles from './index.less';

const { Text } = Typography;

/**上传Json文件 */
const UploadJsonFile: React.FC<{
  /**打开抽屉 */
  open: boolean;
  /**关闭抽屉 */
  closeFun: (text?: 'success' | 'error') => void;
  onOk: (fileList: any) => Promise<void>;
}> = ({ open, closeFun, onOk }) => {
  const { message } = App.useApp();
  /** 确定按钮加载 */
  const [loading, setLoading] = useState(false);
  const [fileList, setFileList] = useState<any>(null);
  const [fileError, setFileError] = useState<string | null>(null);

  /**关闭弹窗 */
  const closeModal = (text?: 'success' | 'error') => {
    setLoading(false);
    setFileList(null);
    setFileError(null);
    closeFun(text);
  };

  /**
   * 验证JSON文件内容
   * @param file 文件对象
   */
  const validateJsonContent = (file: File): Promise<boolean> => {
    return new Promise((resolve) => {
      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          if (e.target?.result) {
            /** 尝试解析JSON */
            JSON.parse(e.target.result as string);
            setFileError(null);
            resolve(true);
          } else {
            setFileError('文件内容为空');
            resolve(false);
          }
        } catch (error) {
          setFileError('文件格式错误，请上传有效的JSON文件');
          resolve(false);
        }
      };
      reader.onerror = () => {
        setFileError('读取文件失败');
        resolve(false);
      };
      reader.readAsText(file);
    });
  };

  /** 删除选中的文件 */
  const handleDeleteFile = () => {
    setFileList(null);
    setFileError(null);
    message.success('已删除选中文件');
  };

  return (
    <Modal
      title={'上传文件'}
      open={open}
      onOk={async () => {
        if (!fileList) {
          message.error('请先选择文件');
          return;
        }
        setLoading(true);
        try {
          await onOk(fileList);
          closeModal('success');
        } catch (error: any) {
          setLoading(false);
        }
      }}
      onCancel={() => closeModal()}
      okText={'确定'}
      cancelText={'取消'}
      width={520}
      okButtonProps={{
        disabled: !fileList || !!fileError,
      }}
      maskClosable={false}
      confirmLoading={loading}
      getContainer={false}
      destroyOnHidden={true}
    >
      <Dragger
        name="file"
        multiple={false}
        showUploadList={false}
        beforeUpload={async (file: any) => {
          setLoading(true);
          setFileError(null);
          const maxFileSize = file.size / 1024 / 1024;
          /**文件大小小于10M，格式只支持json */
          if (maxFileSize > 10) {
            setFileError('文件大小不能超过10M');
            setLoading(false);
            return Upload.LIST_IGNORE;
          }
          if (
            file.type !== 'application/json' &&
            !file.name.endsWith('.json')
          ) {
            setFileError('只支持上传JSON格式文件');
            setLoading(false);
            return Upload.LIST_IGNORE;
          }
          /** 验证JSON内容 */
          const isValidJson = await validateJsonContent(file);
          if (isValidJson) {
            setFileList(file);
          } else {
            /** fileError已在validateJsonContent中设置 */
            message.error(fileError || '文件验证失败');
          }
          setLoading(false);
          return false;
        }}
        style={{ margin: '24px 0' }}
      >
        <p className="ant-upload-drag-icon">
          {loading ? (
            <Row style={{ margin: 22 }}>
              <Loading />
            </Row>
          ) : (
            <InboxOutlined />
          )}
        </p>
        <p className="ant-upload-text">点击上传或者将文件拖动至此处</p>
        <p className="ant-upload-hint">文件最大限制: 10M 格式：JSON</p>
      </Dragger>

      {fileError && (
        <Alert
          message="文件错误"
          description={fileError}
          type="error"
          showIcon
          style={{ marginTop: 16 }}
        />
      )}

      {/* 文件列表 */}
      {fileList?.name && !fileError && (
        <div className={styles.fileContainer}>
          <FileOutlined style={{ color: '#52c41a' }} />
          <Text
            ellipsis={{ tooltip: fileList.name }}
            style={{ maxWidth: '40%', color: 'rgba(0, 0, 0, 0.85)' }}
          >
            {fileList.name}
          </Text>
          <Text type="success" style={{ flex: 1 }}>
            文件已选择，点击确定开始导入
          </Text>
          <Button
            type="text"
            danger
            icon={<DeleteOutlined />}
            onClick={handleDeleteFile}
            className={styles.deleteButton}
            title="删除文件"
          />
        </div>
      )}
    </Modal>
  );
};
export default UploadJsonFile;
