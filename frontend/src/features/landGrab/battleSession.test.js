import {
  BATTLE_PHASE,
  CONNECTION_STATUS,
  battleSessionReducer,
  createBattleSessionState,
} from './battleSession';

test('loads a PvP match into a ready session with its initial board', () => {
  const state = battleSessionReducer(createBattleSessionState(), {
    type: 'LOAD_PVP',
    matchId: 'match-7',
    role: 'p2',
    map: { walls: [[1, 2]], coins: [[3, 4]] },
  });

  expect(state).toMatchObject({
    mode: 'PvP',
    matchId: 'match-7',
    myRole: 'p2',
    phase: BATTLE_PHASE.READY,
    connection: CONNECTION_STATUS.CONNECTING,
  });
  expect(state.gameData.logs[0]).toMatchObject({
    walls: [[1, 2]],
    coins: [[3, 4]],
    p1: { pos: [0, 0], alive: true },
    p2: { pos: [14, 14], alive: true },
  });
});

test('moves a submitted PvP match through result and replay states', () => {
  const ready = battleSessionReducer(createBattleSessionState(), {
    type: 'LOAD_PVP',
    matchId: 'match-8',
    role: 'p1',
    map: {},
  });
  const submitted = battleSessionReducer(ready, { type: 'SUBMIT_STARTED' });
  const finished = battleSessionReducer(submitted, {
    type: 'MATCH_FINISHED',
    gameData: { winner: 'p1', logs: [] },
  });

  expect(submitted.phase).toBe(BATTLE_PHASE.SUBMITTED);
  expect(finished.phase).toBe(BATTLE_PHASE.FINISHED);
  expect(battleSessionReducer(finished, { type: 'SHOW_REPLAY' }).phase)
    .toBe(BATTLE_PHASE.REPLAY);
});

test('keeps a reconnect warning until the socket recovers', () => {
  const ready = battleSessionReducer(createBattleSessionState(), {
    type: 'LOAD_PVP',
    matchId: 'match-9',
    role: 'p1',
    map: {},
  });
  const reconnecting = battleSessionReducer(ready, {
    type: 'SOCKET_RECONNECTING',
    attempt: 2,
  });
  const connected = battleSessionReducer(reconnecting, { type: 'SOCKET_CONNECTED' });

  expect(reconnecting.connection).toBe(CONNECTION_STATUS.RECONNECTING);
  expect(reconnecting.notice).toMatchObject({ code: 'CONNECTION_LOST', level: 'warning' });
  expect(connected.connection).toBe(CONNECTION_STATUS.CONNECTED);
  expect(connected.notice).toBeNull();
});
