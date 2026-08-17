const { buildFrontend, startServer } = require('./serve-build');

module.exports = async (config) => {
  const baseUrl = new URL(config.projects[0].use.baseURL);
  const backendUrl = process.env.PLAYWRIGHT_API_BASE_URL || 'http://localhost:8080';
  const port = Number(baseUrl.port || (baseUrl.protocol === 'https:' ? 443 : 80));

  buildFrontend({
    ...process.env,
    REACT_APP_API_BASE_URL: backendUrl,
  });
  const server = await startServer(port);

  return () => new Promise((resolve, reject) => {
    server.close((error) => error ? reject(error) : resolve());
  });
};
