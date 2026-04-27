/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  // Portal 独立端口运行，不需要 basePath
  // basePath 仅在同一域名下部署多应用时使用
  reactStrictMode: true,
  transpilePackages: ['@lazyday/types', '@lazyday/utils', '@lazyday/api-client', '@lazyday/ui'],
};

module.exports = nextConfig;
