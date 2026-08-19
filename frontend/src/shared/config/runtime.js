const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export const API_BASE_URL = configuredApiBaseUrl.replace(/\/$/, '');

export const apiUrl = (path) => {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${API_BASE_URL}${normalizedPath}`;
};

export const STOMP_ENDPOINT = apiUrl('/ws-stomp');
