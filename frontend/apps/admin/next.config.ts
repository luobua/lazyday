/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  basePath: '/admin',
  assetPrefix: '/admin/',
  reactStrictMode: true,
  transpilePackages: ['@lazyday/types', '@lazyday/utils', '@lazyday/api-client', '@lazyday/ui'],
};

module.exports = nextConfig;
