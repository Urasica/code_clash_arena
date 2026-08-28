import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import Lobby from './Lobby';
import { getSession } from './features/auth/authApi';
import { createStompClient } from './shared/realtime/createStompClient';

vi.mock('./features/auth/authApi', () => ({
  getSession: vi.fn(),
}));

vi.mock('./shared/realtime/createStompClient', () => ({
  createStompClient: vi.fn(),
}));

const createFakeClient = () => {
  const subscriptions = [];
  const client = {
    connected: true,
    activate: vi.fn(),
    deactivate: vi.fn(() => Promise.resolve()),
    publish: vi.fn(),
    subscribe: vi.fn((destination, callback) => {
      const subscription = { unsubscribe: vi.fn() };
      subscriptions.push({ destination, callback, subscription });
      return subscription;
    }),
  };
  return { client, subscriptions };
};

const renderLobby = () => render(
  <Lobby
    isLoggedIn
    userInfo={{ userId: 7, nickname: 'Matcher' }}
    onStartGame={vi.fn()}
    onRequestLogin={vi.fn()}
    onLogout={vi.fn()}
  />,
);

beforeEach(() => {
  vi.clearAllMocks();
  getSession.mockResolvedValue({ status: 200, data: { userId: 7 } });
});

test('keeps one matchmaking subscription when connect is repeated', async () => {
  const { client, subscriptions } = createFakeClient();
  createStompClient.mockReturnValue(client);
  renderLobby();

  fireEvent.click(screen.getByRole('heading', { name: 'LAND GRAB' }));
  fireEvent.click(screen.getByRole('button', { name: '매칭 시작' }));
  await waitFor(() => expect(client.activate).toHaveBeenCalledOnce());
  const socketCallbacks = createStompClient.mock.calls[0][0];

  act(() => socketCallbacks.onConnect());
  act(() => socketCallbacks.onConnect());

  expect(subscriptions).toHaveLength(2);
  expect(subscriptions[0].subscription.unsubscribe).toHaveBeenCalledOnce();
  expect(
    client.publish.mock.calls.filter(([message]) => message.destination === '/app/match/join'),
  ).toHaveLength(2);

  act(() => socketCallbacks.onWebSocketClose({ code: 1006, reason: '' }));
  expect(screen.getByRole('heading', { name: 'RECONNECTING...' })).toBeInTheDocument();
  expect(await screen.findByRole('alert')).toHaveTextContent('매칭 서버에 다시 연결하고 있습니다');
});

test('stops matchmaking and offers login when the broker session expired', async () => {
  const { client } = createFakeClient();
  createStompClient.mockReturnValue(client);
  const onRequestLogin = vi.fn();

  render(
    <Lobby
      isLoggedIn
      userInfo={{ userId: 7, nickname: 'Matcher' }}
      onStartGame={vi.fn()}
      onRequestLogin={onRequestLogin}
      onLogout={vi.fn()}
    />,
  );
  fireEvent.click(screen.getByRole('heading', { name: 'LAND GRAB' }));
  fireEvent.click(screen.getByRole('button', { name: '매칭 시작' }));
  await waitFor(() => expect(client.activate).toHaveBeenCalledOnce());
  const socketCallbacks = createStompClient.mock.calls[0][0];

  act(() => socketCallbacks.onStompError({
    headers: { message: 'Unauthorized' },
    body: 'session expired',
  }));

  expect(screen.getByRole('alert')).toHaveTextContent('로그인 세션이 만료되었습니다');
  fireEvent.click(screen.getByRole('button', { name: '다시 로그인' }));
  expect(onRequestLogin).toHaveBeenCalledOnce();
});
