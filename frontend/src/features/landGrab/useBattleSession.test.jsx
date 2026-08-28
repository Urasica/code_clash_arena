import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import { useBattleSession } from './useBattleSession';
import { startLandGrabMatch } from './landGrabApi';
import { getSession } from '../auth/authApi';
import { createStompClient } from '../../shared/realtime/createStompClient';

vi.mock('./landGrabApi', () => ({
  startLandGrabMatch: vi.fn(),
  compileLandGrabCode: vi.fn(),
  runLandGrabMatch: vi.fn(),
}));

vi.mock('../../shared/realtime/createStompClient', () => ({
  createStompClient: vi.fn(),
}));

vi.mock('../auth/authApi', () => ({
  getSession: vi.fn(),
}));

const SessionHarness = ({ matchData = null }) => {
  const battle = useBattleSession({ difficulty: 'normal', matchData });
  return (
    <div>
      <span data-testid="phase">{battle.phase}</span>
      <span data-testid="connection">{battle.connection}</span>
      <span data-testid="notice">{battle.notice?.code || ''}</span>
      <button onClick={battle.startMatch}>start</button>
      <button onClick={battle.submitCode}>submit</button>
    </div>
  );
};

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

beforeEach(() => {
  vi.clearAllMocks();
  getSession.mockResolvedValue({ status: 200, data: { userId: 7 } });
});

test('replaces duplicate subscriptions and never republishes a submitted code after reconnect', async () => {
  const { client, subscriptions } = createFakeClient();
  createStompClient.mockReturnValue(client);
  const matchData = {
    matchId: 'pvp-42',
    myRole: 'p1',
    mapData: { walls: [], coins: [] },
  };

  render(<SessionHarness matchData={matchData} />);
  await waitFor(() => expect(client.activate).toHaveBeenCalledOnce());
  const socketCallbacks = createStompClient.mock.calls[0][0];

  act(() => socketCallbacks.onConnect());
  expect(screen.getByTestId('connection')).toHaveTextContent('connected');
  expect(subscriptions).toHaveLength(1);

  act(() => socketCallbacks.onConnect());
  expect(subscriptions).toHaveLength(2);
  expect(subscriptions[0].subscription.unsubscribe).toHaveBeenCalledOnce();

  fireEvent.click(screen.getByRole('button', { name: 'submit' }));
  fireEvent.click(screen.getByRole('button', { name: 'submit' }));
  expect(
    client.publish.mock.calls.filter(([message]) => message.destination === '/app/game/submit'),
  ).toHaveLength(1);

  client.connected = false;
  act(() => socketCallbacks.onWebSocketClose({ code: 1006, reason: '' }));
  expect(screen.getByTestId('connection')).toHaveTextContent('reconnecting');

  client.connected = true;
  act(() => socketCallbacks.onConnect());
  expect(subscriptions).toHaveLength(3);
  expect(
    client.publish.mock.calls.filter(([message]) => message.destination === '/app/game/submit'),
  ).toHaveLength(1);
  expect(
    client.publish.mock.calls.filter(([message]) => message.destination === '/app/game/join'),
  ).toHaveLength(3);

  act(() => subscriptions[2].callback({
    body: JSON.stringify({ type: 'RESULT', winner: 'p1', logs: [] }),
  }));
  expect(screen.getByTestId('phase')).toHaveTextContent('finished');
});

test('moves an unauthorized REST response to the expired session state', async () => {
  startLandGrabMatch.mockRejectedValueOnce({ response: { status: 401 } });

  render(<SessionHarness />);
  fireEvent.click(screen.getByRole('button', { name: 'start' }));

  await waitFor(() => expect(screen.getByTestId('phase')).toHaveTextContent('expired'));
  expect(screen.getByTestId('notice')).toHaveTextContent('SESSION_EXPIRED');
});

test('moves an unauthorized broker response to the expired session state', async () => {
  const { client } = createFakeClient();
  createStompClient.mockReturnValue(client);

  render(<SessionHarness matchData={{ matchId: 'pvp-43', myRole: 'p2', mapData: {} }} />);
  await waitFor(() => expect(client.activate).toHaveBeenCalledOnce());
  const socketCallbacks = createStompClient.mock.calls[0][0];

  act(() => socketCallbacks.onStompError({
    headers: { message: 'Unauthorized' },
    body: 'session expired',
  }));

  expect(screen.getByTestId('phase')).toHaveTextContent('expired');
  expect(screen.getByTestId('notice')).toHaveTextContent('SESSION_EXPIRED');
  expect(client.deactivate).toHaveBeenCalled();
});

test('detects an expired cookie while a disconnected socket is retrying', async () => {
  const { client } = createFakeClient();
  createStompClient.mockReturnValue(client);
  getSession.mockRejectedValueOnce({ response: { status: 401 } });

  render(<SessionHarness matchData={{ matchId: 'pvp-44', myRole: 'p1', mapData: {} }} />);
  await waitFor(() => expect(client.activate).toHaveBeenCalledOnce());
  const socketCallbacks = createStompClient.mock.calls[0][0];

  act(() => socketCallbacks.onWebSocketClose({ code: 1006, reason: '' }));

  await waitFor(() => expect(screen.getByTestId('phase')).toHaveTextContent('expired'));
  expect(getSession).toHaveBeenCalledOnce();
  expect(client.deactivate).toHaveBeenCalled();
});
