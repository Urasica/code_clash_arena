import { Client, ReconnectionTimeMode } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { STOMP_ENDPOINT } from '../config/runtime';

export const STOMP_RECONNECT_CONFIG = Object.freeze({
  reconnectDelay: 1_000,
  maxReconnectDelay: 10_000,
  reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
  connectionTimeout: 10_000,
  heartbeatIncoming: 10_000,
  heartbeatOutgoing: 10_000,
  discardWebsocketOnCommFailure: true,
});

export const createStompClient = (options = {}) => new Client({
  ...STOMP_RECONNECT_CONFIG,
  ...options,
  webSocketFactory: () => new SockJS(STOMP_ENDPOINT),
});
