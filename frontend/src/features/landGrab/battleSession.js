import { TEMPLATES } from '../../CodeTemplates';

export const BATTLE_PHASE = Object.freeze({
  INIT: 'init',
  READY: 'ready',
  RUNNING: 'running',
  SUBMITTED: 'submitted',
  FINISHED: 'finished',
  REPLAY: 'replay',
  EXPIRED: 'expired',
});

export const CONNECTION_STATUS = Object.freeze({
  IDLE: 'idle',
  CONNECTING: 'connecting',
  CONNECTED: 'connected',
  RECONNECTING: 'reconnecting',
  FAILED: 'failed',
});

export const BATTLE_TIME_LIMIT_SECONDS = 600;

export const createInitialGameData = (map) => {
  const boardSize = 15;
  const walls = map?.walls || [];
  const coins = map?.coins || [];
  const board = Array.from(
    { length: boardSize },
    () => Array(boardSize).fill(0),
  );

  board[0][0] = 1;
  board[boardSize - 1][boardSize - 1] = 2;

  return {
    logs: [{
      turn: 0,
      board_size: boardSize,
      walls,
      coins,
      p1: { pos: [0, 0], alive: true },
      p2: { pos: [boardSize - 1, boardSize - 1], alive: true },
      board,
    }],
  };
};

export const resolvePvpMap = (matchData) => (
  matchData?.mapData?.map || matchData?.mapData || {}
);

export const createBattleSessionState = () => ({
  mode: 'AI',
  matchId: null,
  gameData: null,
  phase: BATTLE_PHASE.INIT,
  busyLabel: null,
  language: 'python',
  userCode: TEMPLATES.python,
  timeLeft: BATTLE_TIME_LIMIT_SECONDS,
  opponentSubmitted: false,
  myRole: null,
  connection: CONNECTION_STATUS.IDLE,
  reconnectAttempt: 0,
  notice: null,
});

const reconnectNotice = (attempt) => ({
  code: 'CONNECTION_LOST',
  level: 'warning',
  title: '연결을 복구하고 있습니다',
  message: `서버 연결이 일시적으로 끊겼습니다. 재연결을 시도합니다${attempt > 0 ? ` (${attempt}회)` : ''}.`,
});

export const battleSessionReducer = (state, action) => {
  switch (action.type) {
    case 'RESET_AI':
      return createBattleSessionState();
    case 'LOAD_PVP':
      return {
        ...createBattleSessionState(),
        mode: 'PvP',
        matchId: action.matchId,
        gameData: createInitialGameData(action.map),
        phase: BATTLE_PHASE.READY,
        myRole: action.role,
        connection: CONNECTION_STATUS.CONNECTING,
      };
    case 'AI_START_REQUEST':
      return {
        ...state,
        busyLabel: 'GENERATING...',
        notice: null,
      };
    case 'AI_START_SUCCESS':
      return {
        ...state,
        matchId: action.matchId,
        gameData: createInitialGameData(action.map),
        phase: BATTLE_PHASE.READY,
        busyLabel: null,
        timeLeft: BATTLE_TIME_LIMIT_SECONDS,
        opponentSubmitted: false,
        notice: null,
      };
    case 'SET_LANGUAGE':
      return {
        ...state,
        language: action.language,
        userCode: TEMPLATES[action.language] || '',
      };
    case 'SET_CODE':
      return { ...state, userCode: action.code || '' };
    case 'TICK':
      if (state.phase !== BATTLE_PHASE.READY) return state;
      return { ...state, timeLeft: Math.max(0, state.timeLeft - 1) };
    case 'SUBMIT_STARTED':
      return {
        ...state,
        phase: state.mode === 'PvP' ? BATTLE_PHASE.SUBMITTED : BATTLE_PHASE.RUNNING,
        busyLabel: action.busyLabel || (state.mode === 'PvP' ? 'PROCESSING...' : 'COMPILING...'),
        notice: action.automatic
          ? {
              code: 'AUTO_SUBMITTED',
              level: 'info',
              title: '시간 제한이 종료되었습니다',
              message: '현재 코드가 자동으로 제출되었습니다.',
            }
          : null,
      };
    case 'BUSY_LABEL_CHANGED':
      return { ...state, busyLabel: action.busyLabel };
    case 'OPPONENT_SUBMITTED':
      return { ...state, opponentSubmitted: true };
    case 'MATCH_FINISHED':
      return {
        ...state,
        gameData: action.gameData,
        phase: BATTLE_PHASE.FINISHED,
        busyLabel: null,
        notice: null,
      };
    case 'SHOW_REPLAY':
      return { ...state, phase: BATTLE_PHASE.REPLAY };
    case 'SOCKET_CONNECTED':
      return {
        ...state,
        connection: CONNECTION_STATUS.CONNECTED,
        reconnectAttempt: 0,
        notice: ['CONNECTION_LOST', 'SOCKET_UNAVAILABLE', 'SOCKET_ERROR'].includes(state.notice?.code)
          ? null
          : state.notice,
      };
    case 'SOCKET_RECONNECTING':
      if ([BATTLE_PHASE.FINISHED, BATTLE_PHASE.EXPIRED].includes(state.phase)) {
        return state;
      }
      return {
        ...state,
        connection: CONNECTION_STATUS.RECONNECTING,
        reconnectAttempt: action.attempt,
        notice: reconnectNotice(action.attempt),
      };
    case 'RECOVERABLE_ERROR':
      return {
        ...state,
        phase: action.phase || state.phase,
        busyLabel: null,
        notice: {
          code: action.code || 'BATTLE_ERROR',
          level: 'error',
          title: action.title || '요청을 완료하지 못했습니다',
          message: action.message,
        },
      };
    case 'SESSION_EXPIRED':
      return {
        ...state,
        phase: BATTLE_PHASE.EXPIRED,
        busyLabel: null,
        connection: CONNECTION_STATUS.FAILED,
        notice: {
          code: 'SESSION_EXPIRED',
          level: 'error',
          title: '로그인 세션이 만료되었습니다',
          message: '안전한 이용을 위해 로비로 돌아간 뒤 다시 로그인해 주세요.',
        },
      };
    case 'CLEAR_NOTICE':
      return { ...state, notice: null };
    default:
      return state;
  }
};

export const formatBattleTime = (seconds) => {
  const minutes = Math.floor(seconds / 60).toString().padStart(2, '0');
  const remainder = (seconds % 60).toString().padStart(2, '0');
  return `${minutes}:${remainder}`;
};
