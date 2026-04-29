/** @type {import('next').NextConfig} */
const isProd = process.env.NODE_ENV === 'production';

const nextConfig = {
  output: 'standalone',
  basePath: '/admin',
  // assetPrefix 仅生产环境需要（Nginx 反向代理时静态资源路径要带 /admin 前缀）
  // dev 模式下不设置，否则 /_next/static 资源会 404
  assetPrefix: isProd ? '/admin/' : undefined,
  reactStrictMode: true,
  transpilePackages: ['@lazyday/types', '@lazyday/utils', '@lazyday/api-client', '@lazyday/ui'],
};

module.exports = nextConfig;
