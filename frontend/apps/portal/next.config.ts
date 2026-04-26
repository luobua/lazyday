/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  // Portal 应用挂载到 /portal 路径
  basePath: '/portal',
  // 资源前缀
  assetPrefix: '/portal/',
  reactStrictMode: true,
  transpilePackages: ['@lazyday/types', '@lazyday/utils', '@lazyday/api-client', '@lazyday/ui'],
};

module.exports = nextConfig;
