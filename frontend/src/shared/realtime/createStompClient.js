import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { STOMP_ENDPOINT } from '../config/runtime';

export const createStompClient = (options = {}) => new Client({
  ...options,
  webSocketFactory: () => new SockJS(STOMP_ENDPOINT),
});
