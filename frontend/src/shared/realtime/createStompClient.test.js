import { ReconnectionTimeMode } from '@stomp/stompjs';
import { createStompClient } from './createStompClient';

test('uses bounded exponential reconnect and heartbeat defaults', () => {
  const client = createStompClient();

  expect(client.reconnectDelay).toBe(1_000);
  expect(client.maxReconnectDelay).toBe(10_000);
  expect(client.reconnectTimeMode).toBe(ReconnectionTimeMode.EXPONENTIAL);
  expect(client.connectionTimeout).toBe(10_000);
  expect(client.heartbeatIncoming).toBe(10_000);
  expect(client.heartbeatOutgoing).toBe(10_000);
  expect(client.discardWebsocketOnCommFailure).toBe(true);
});
