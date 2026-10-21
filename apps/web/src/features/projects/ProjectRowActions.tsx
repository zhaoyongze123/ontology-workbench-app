import ProjectMembersDialog from './ProjectMembersDialog';
import { getProjectAccess, leaveProject, type ProjectAccess } from './service';
import { DeleteOutlined, EditOutlined, EyeOutlined, TeamOutlined } from '@ant-design/icons';
import { App, Button, Popconfirm, Row } from 'antd';
import React, { useEffect, useState } from 'react';
import { history } from 'umi';

type Props = {
  projectId: string;
  onEdit: () => void;
  onDelete: () => void;
  onLeave: () => void;
};

const ProjectRowActions: React.FC<Props> = ({ projectId, onEdit, onDelete, onLeave }) => {
  const { message } = App.useApp();
  const [access, setAccess] = useState<ProjectAccess | null>(null);
  const [membersOpen, setMembersOpen] = useState(false);
  useEffect(() => {
    let active = true;
    getProjectAccess(projectId)
      .then((response) => active && setAccess(response.data || null))
      .catch((error) => {
        if (active) {
          setAccess(null);
          message.error((error as Error)?.message || '无法读取项目权限');
        }
      });
    return () => { active = false; };
  }, [projectId]);

  const capabilities = access?.capabilities || [];
  return <>
    <Row className="tableAction" wrap={false}>
      <Button type="link" onClick={() => history.push(`/manage/${projectId}`)}><EyeOutlined /> 详情</Button>
      {capabilities.includes('EDIT_PROJECT') ? <Button type="link" onClick={onEdit}><EditOutlined /> 编辑</Button> : null}
      {capabilities.includes('DELETE_PROJECT') ? <Button type="link" danger onClick={onDelete}><DeleteOutlined /> 删除</Button> : null}
      {capabilities.includes('MANAGE_MEMBERS') ? <Button type="link" onClick={() => setMembersOpen(true)}><TeamOutlined /> 成员</Button> : null}
      {access && access.role !== 'OWNER' && access.role !== 'SYSTEM_ADMIN' ? (
        <Popconfirm title="退出后将无法访问该项目及其图谱，确定继续吗？" onConfirm={async () => {
          try {
            await leaveProject(projectId);
            message.success('已退出项目');
            onLeave();
          } catch (error) {
            message.error((error as Error)?.message || '退出项目失败');
          }
        }}>
          <Button type="link" danger>退出项目</Button>
        </Popconfirm>
      ) : null}
    </Row>
    {access ? <ProjectMembersDialog projectId={projectId} access={access} open={membersOpen} onClose={() => setMembersOpen(false)} /> : null}
  </>;
};

export default ProjectRowActions;
