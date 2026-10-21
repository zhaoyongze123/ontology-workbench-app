const BEIJING_TIME_ZONE = 'Asia/Shanghai';

/**
 * Instant 带时区时按北京时间显示；遗留 LocalDateTime 按服务容器 Asia/Shanghai 解释，
 * 不把无时区字符串伪装成 UTC。服务镜像显式配置了 Asia/Shanghai。
 */
export function formatAdminDateTime(value?: string | null): string {
  if (!value) return '—';
  const hasZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value);
  const normalized = hasZone ? value : `${value}+08:00`;
  const parsed = new Date(normalized);
  if (Number.isNaN(parsed.getTime())) return '时间格式无效';

  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: BEIJING_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(parsed);
  const get = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value || '';
  return `${get('year')}-${get('month')}-${get('day')} ${get('hour')}:${get(
    'minute',
  )}:${get('second')}`;
}

export const ADMIN_TIME_ZONE_LABEL = '北京时间（UTC+08:00）';
