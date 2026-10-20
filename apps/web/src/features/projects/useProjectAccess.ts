import { getProjectAccess, type ProjectAccess, type ProjectCapability } from './service';
import { useEffect, useState } from 'react';

/** 权限信息来自服务端 capability 响应；只用于界面呈现，后端仍是最终授权边界。 */
export function useProjectAccess(projectId: string) {
  const [access, setAccess] = useState<ProjectAccess | null>(null);
  const [loading, setLoading] = useState(Boolean(projectId));
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    if (!projectId) {
      setAccess(null);
      setLoading(false);
      return () => { active = false; };
    }
    setLoading(true);
    setError('');
    getProjectAccess(projectId)
      .then((result) => active && setAccess(result.data || null))
      .catch((reason) => {
        if (active) {
          setAccess(null);
          setError((reason as Error)?.message || '无法读取项目权限');
        }
      })
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [projectId]);
  const can = (capability: ProjectCapability) => Boolean(access?.capabilities?.includes(capability));
  return { access, loading, error, can };
}
