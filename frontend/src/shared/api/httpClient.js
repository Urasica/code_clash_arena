import axios from 'axios';
import { API_BASE_URL } from '../config/runtime';

const httpClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
});

export default httpClient;
