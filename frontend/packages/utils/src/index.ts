import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import duration from 'dayjs/plugin/duration';

dayjs.extend(relativeTime);
dayjs.extend(duration);

/**
 * 格式化时间戳为可读字符串
 */
export function formatTimestamp(
  timestamp: string | number | Date,
  format = 'YYYY-MM-DD HH:mm:ss',
): string {
  return dayjs(timestamp).format(format);
}

/**
 * 相对时间（如 "3 分钟前"）
 */
export function formatRelativeTime(timestamp: string | number | Date): string {
  return dayjs(timestamp).fromNow();
}

/**
 * 格式化延迟
 */
export function formatLatency(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`;
  return `${(ms / 60_000).toFixed(1)}m`;
}

/**
 * 格式化数字（千分位）
 */
export function formatNumber(num: number): string {
  return new Intl.NumberFormat().format(num);
}

/**
 * 复制文本到剪贴板
 */
export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

/**
 * 获取状态码颜色
 */
export function getStatusCodeColor(status: number): string {
  if (status >= 200 && status < 300) return 'success';
  if (status >= 300 && status < 400) return 'processing';
  if (status >= 400 && status < 500) return 'warning';
  return 'error';
}

/**
 * AppKey 脱敏显示
 */
export function maskAppKey(appKey: string): string {
  if (appKey.length <= 8) return appKey;
  return `${appKey.slice(0, 4)}${'•'.repeat(appKey.length - 8)}${appKey.slice(-4)}`;
}

/**
 * 构建类名（简易 clsx）
 */
export function cn(...classes: (string | boolean | undefined | null)[]): string {
  return classes.filter(Boolean).join(' ');
}
