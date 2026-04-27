import { redirect } from 'next/navigation';

/**
 * / → 重定向到 /login（未登录）或 /overview（已登录）
 * 由于 Middleware 已处理认证重定向，这里直接重定向到概览
 */
export default function PortalIndex() {
  redirect('/overview');
}
