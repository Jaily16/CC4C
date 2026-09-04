import { constants } from 'node:fs';
import { lstat, open, realpath } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, URL } from 'node:url';

import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

const uploadContentTypes = new Map([
  ['.png', 'image/png'],
  ['.jpg', 'image/jpeg'],
  ['.jpeg', 'image/jpeg'],
  ['.gif', 'image/gif'],
  ['.webp', 'image/webp'],
  ['.avif', 'image/avif'],
  ['.bmp', 'image/bmp'],
  ['.ico', 'image/x-icon'],
  ['.svg', 'image/svg+xml'],
]);

/** 比较规范化的绝对路径；Windows 忽略盘符和目录的大小写，但不接受链接目标替换路径。 */
function sameUploadPath(left, right) {
  return process.platform === 'win32' ? left.toLowerCase() === right.toLowerCase() : left === right;
}

/**
 * 逐级检查路径的元数据，不枚举目录。只接受普通目录以及可选的末尾普通文件，拒绝符号链接、
 * junction、文件硬链接和真实路径偏移。启动时允许目录尚未创建；请求时缺失即按不可访问处理。
 */
async function inspectUploadPath(absolutePath, { file = false, allowMissing = false } = {}) {
  let current = path.parse(absolutePath).root;
  const segments = path.relative(current, absolutePath).split(path.sep);
  let metadata;
  for (let index = 0; index < segments.length; index += 1) {
    current = path.join(current, segments[index]);
    try {
      metadata = await lstat(current);
    } catch (error) {
      if (allowMissing && error.code === 'ENOENT') return;
      throw error;
    }
    const isFile = file && index === segments.length - 1;
    if (metadata.isSymbolicLink() || (isFile ? !metadata.isFile() || metadata.nlink !== 1 : !metadata.isDirectory())) {
      throw new Error('Upload paths must not contain links or special files.');
    }
    if (!sameUploadPath(await realpath(current), current)) {
      throw new Error('Upload paths must not redirect to another location.');
    }
  }
  return metadata;
}

/** 返回不包含路径、配置值或异常细节的错误，且不回退到旧 public 上传目录或 SPA 首页。 */
function rejectUploadResponse(response, statusCode = 404) {
  if (response.headersSent) {
    response.destroy();
    return;
  }
  response.statusCode = statusCode;
  response.removeHeader('Content-Length');
  response.setHeader('Cache-Control', 'no-store');
  response.setHeader('X-Content-Type-Options', 'nosniff');
  response.end();
}

/**
 * 仅打开后端生成的单个上传文件。检查结果与已打开句柄的文件身份必须一致，避免路径被替换后
 * 错读其他文件；文件句柄在 HEAD、异常、客户端断开或流完成时关闭，全程不写入上传目录。
 */
async function sendUploadFile(request, response, root, relativePath) {
  const filename = path.join(root, ...relativePath.split('/'));
  const metadata = await inspectUploadPath(filename, { file: true });
  let handle;
  try {
    handle = await open(filename, constants.O_RDONLY | (constants.O_NOFOLLOW ?? 0) | (constants.O_NONBLOCK ?? 0));
    const opened = await handle.stat();
    if (!opened.isFile() || opened.nlink !== 1 || opened.dev !== metadata.dev || opened.ino !== metadata.ino) {
      throw new Error('Upload file changed while opening.');
    }
    const contentType = uploadContentTypes.get(path.extname(filename).toLowerCase());
    response.setHeader('Content-Type', contentType || 'application/octet-stream');
    response.setHeader('Content-Length', opened.size);
    response.setHeader('Cache-Control', 'no-store');
    response.setHeader('X-Content-Type-Options', 'nosniff');
    // 上传内容不作为同源页面执行；SVG 也不能执行脚本，未知类型仅作为下载内容返回。
    response.setHeader('Content-Security-Policy', "default-src 'none'; sandbox");
    if (!contentType) response.setHeader('Content-Disposition', 'attachment');
    if (request.method === 'HEAD' || response.destroyed) {
      response.end();
      return;
    }
    const stream = handle.createReadStream({ autoClose: true });
    response.once('close', () => stream.destroy());
    stream.once('error', () => rejectUploadResponse(response));
    stream.pipe(response);
    handle = null; // 句柄所有权交给流，由 autoClose 负责关闭。
  } finally {
    if (handle) await handle.close();
  }
}

/**
 * 只在宿主启动脚本明确传入两个上传目录时启用开发服务器映射；不读取任何后端环境文件。
 * 这两个变量不带 VITE_ 前缀，不会进入浏览器配置。插件仅用于 Dev，不参与生产构建或扩展 /@fs/。
 */
function hostUploadPlugin() {
  return {
    name: 'cc4c-host-uploads',
    apply: 'serve',
    async configureServer(server) {
      const roots = [process.env.CC4C_HOST_BLOG_IMG_ROOT, process.env.CC4C_HOST_AVATAR_ROOT];
      if (roots.every((root) => root === undefined)) return;
      if (roots.some((root) => !root || !path.isAbsolute(root) || path.resolve(root) === path.parse(root).root)) {
        throw new Error('Host uploads require two explicit absolute directory paths.');
      }
      const normalizedRoots = roots.map((root) => path.resolve(root));
      if (sameUploadPath(...normalizedRoots)) throw new Error('Host upload roots must be distinct.');
      try {
        for (const root of normalizedRoots) await inspectUploadPath(root, { allowMissing: true });
      } catch {
        throw new Error('Host upload directories must not contain links or special files.');
      }
      const routes = ['/blogImg/', '/avatar/'].map((prefix, index) => ({ prefix, root: normalizedRoots[index] }));
      server.middlewares.use((request, response, next) => {
        let pathname;
        try {
          // 只解码一次，并在解码后匹配路由；双重编码、反斜杠、点段和额外子目录均不能通过文件名门禁。
          pathname = decodeURIComponent((request.url || '').split('?')[0]);
        } catch {
          rejectUploadResponse(response);
          return;
        }
        // 同时识别 Windows 分隔符和点段归一化后的别名，防止异常路径绕过本映射落入旧 public 目录。
        const canonicalPath = path.posix.normalize(pathname.replaceAll('\\', '/'));
        const route = routes.find(({ prefix }) =>
          [pathname, canonicalPath].some(
            (candidate) =>
              candidate.toLowerCase().startsWith(prefix.toLowerCase()) ||
              candidate.toLowerCase() === prefix.slice(0, -1).toLowerCase(),
          ),
        );
        if (!route) {
          next();
          return;
        }
        if (pathname !== canonicalPath || pathname.includes('\\')) {
          rejectUploadResponse(response);
          return;
        }
        if (!/^(localhost|127\.0\.0\.1|\[::1\])(?::\d+)?$/i.test(request.headers.host || '')) {
          rejectUploadResponse(response, 403);
          return;
        }
        if (request.method !== 'GET' && request.method !== 'HEAD') {
          response.setHeader('Allow', 'GET, HEAD');
          rejectUploadResponse(response, 405);
          return;
        }
        const relativePath = pathname.slice(route.prefix.length);
        // FileStorage 固定生成 img1..img5 / 32 位十六进制 UUID + 清洗后的原文件名；兼容中文文件名。
        if (!/^img[1-5]\/[0-9a-f]{32}[\p{L}\p{N}._-]+$/u.test(relativePath)) {
          rejectUploadResponse(response);
          return;
        }
        void sendUploadFile(request, response, route.root, relativePath).catch(() => rejectUploadResponse(response));
      });
    },
  };
}

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue(), hostUploadPlugin()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
});
