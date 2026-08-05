import httpClient from '../../shared/api/httpClient';
import { apiUrl } from '../../shared/config/runtime';

export const getSession = () => httpClient.get('/api/auth/me');
export const login = (credentials) => httpClient.post('/api/auth/login', credentials);
export const signup = (account) => httpClient.post('/api/auth/signup', account);
export const loginAsGuest = () => httpClient.post('/api/auth/guest', {});
export const logout = () => httpClient.post('/api/auth/logout', {});

export const GOOGLE_LOGIN_URL = apiUrl('/oauth2/authorization/google');
