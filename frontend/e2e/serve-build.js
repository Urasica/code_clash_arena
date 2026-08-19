const fs = require('fs');
const http = require('http');
const path = require('path');
const { spawnSync } = require('child_process');

const frontendRoot = path.resolve(__dirname, '..');
const buildRoot = path.join(frontendRoot, 'build');

const contentTypes = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.ico': 'image/x-icon',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.webmanifest': 'application/manifest+json',
};

const sendFile = (filePath, response) => {
  fs.readFile(filePath, (error, body) => {
    if (error) {
      response.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
      response.end('Unable to read the frontend build.');
      return;
    }
    response.writeHead(200, {
      'Cache-Control': 'no-store',
      'Content-Type': contentTypes[path.extname(filePath)] || 'application/octet-stream',
    });
    response.end(body);
  });
};

const createServer = () => http.createServer((request, response) => {
  const pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
  const requestedPath = path.resolve(buildRoot, `.${pathname}`);
  const relativePath = path.relative(buildRoot, requestedPath);

  if (relativePath.startsWith('..') || path.isAbsolute(relativePath)) {
    response.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' });
    response.end('Forbidden');
    return;
  }

  fs.stat(requestedPath, (error, stats) => {
    if (!error && stats.isFile()) {
      sendFile(requestedPath, response);
      return;
    }
    sendFile(path.join(buildRoot, 'index.html'), response);
  });
});

const buildFrontend = (environment = process.env) => {
  const npmCli = environment.npm_execpath;
  let command = 'npm';
  let args = ['run', 'build'];
  if (npmCli) {
    command = process.execPath;
    args = [npmCli, 'run', 'build'];
  } else if (process.platform === 'win32') {
    command = environment.ComSpec || 'cmd.exe';
    args = ['/d', '/s', '/c', 'npm.cmd run build'];
  }
  const build = spawnSync(command, args, {
    cwd: frontendRoot,
    env: environment,
    stdio: 'inherit',
  });
  if (build.error) throw build.error;
  if (build.status !== 0) throw new Error(`Frontend build failed with exit code ${build.status}`);
};

const startServer = (port = Number(process.env.PORT || 3000)) => {
  const server = createServer();
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, '0.0.0.0', () => resolve(server));
  });
};

if (require.main === module) {
  buildFrontend();
  startServer().then((server) => {
    const shutdown = () => server.close(() => process.exit(0));
    process.on('SIGINT', shutdown);
    process.on('SIGTERM', shutdown);
  });
}

module.exports = { buildFrontend, startServer };
