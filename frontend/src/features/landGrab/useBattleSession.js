import { useCallback, useEffect, useReducer, useRef } from 'react';
import {
  compileLandGrabCode,
  runLandGrabMatch,
  startLandGrabMatch,
} from './landGrabApi';
import { getSession } from '../auth/authApi';
import {
  BATTLE_PHASE,
  CONNECTION_STATUS,
  battleSessionReducer,
  createBattleSessionState,
  resolvePvpMap,
} from './battleSession';
import { createStompClient } from '../../shared/realtime/createStompClient';
import { isSessionExpiredSignal } from '../auth/sessionErrors';

const errorMessage = (error, fallback) => (
  error?.response?.data?.error || error?.message || fallback
);

export const useBattleSession = ({ difficulty, matchData }) => {
  const [state, dispatch] = useReducer(
    battleSessionReducer,
    undefined,
    createBattleSessionState,
  );
  const stateRef = useRef(state);
  const stompClientRef = useRef(null);
  const submissionLockedRef = useRef(false);

  stateRef.current = state;

  useEffect(() => {
    submissionLockedRef.current = false;

    if (matchData) {
      dispatch({
        type: 'LOAD_PVP',
        matchId: matchData.matchId,
        role: matchData.myRole,
        map: resolvePvpMap(matchData),
      });
    } else {
      dispatch({ type: 'RESET_AI' });
    }
  }, [matchData]);

  useEffect(() => {
    const matchId = matchData?.matchId;
    const role = matchData?.myRole;
    if (!matchId) return undefined;

    let disposed = false;
    let terminal = false;
    let subscription = null;
    let reconnectAttempt = 0;
    let sessionCheckInFlight = false;

    const forgetSubscription = () => {
      subscription = null;
    };

    const unsubscribe = () => {
      if (!subscription) return;
      try {
        subscription.unsubscribe();
      } catch {
        // The previous transport may already be closed during reconnect.
      }
      forgetSubscription();
    };

    const client = createStompClient({
      onConnect: () => {
        if (disposed || terminal) return;

        unsubscribe();
        reconnectAttempt = 0;
        dispatch({ type: 'SOCKET_CONNECTED' });

        subscription = client.subscribe(
          `/topic/game/${matchId}`,
          (message) => {
            if (disposed || terminal) return;
            let result;
            try {
              result = JSON.parse(message.body);
            } catch {
              dispatch({
                type: 'RECOVERABLE_ERROR',
                code: 'INVALID_SOCKET_MESSAGE',
                message: '서버가 보낸 대전 상태를 읽지 못했습니다. 연결은 유지되며 다음 상태를 기다립니다.',
              });
              return;
            }

            if (result.type === 'NOTIFICATION' && result.message === 'PLAYER_SUBMITTED') {
              if (result.role !== role) {
                dispatch({ type: 'OPPONENT_SUBMITTED' });
              }
              return;
            }

            if (result.type === 'RESULT' || result.type === 'ERROR') {
              terminal = true;
              dispatch({ type: 'MATCH_FINISHED', gameData: result });
              void client.deactivate();
            }
          },
          { id: `battle-${matchId}` },
        );

        client.publish({
          destination: '/app/game/join',
          body: JSON.stringify({ matchId }),
        });
      },
      onStompError: (frame) => {
        if (disposed) return;
        if (isSessionExpiredSignal(frame)) {
          terminal = true;
          dispatch({ type: 'SESSION_EXPIRED' });
          void client.deactivate();
          return;
        }

        dispatch({
          type: 'RECOVERABLE_ERROR',
          code: 'SOCKET_ERROR',
          message: '실시간 대전 연결에서 오류가 발생했습니다. 자동 재연결을 기다려 주세요.',
        });
      },
      onWebSocketClose: (event) => {
        forgetSubscription();
        if (disposed || terminal) return;
        if (isSessionExpiredSignal(event)) {
          terminal = true;
          dispatch({ type: 'SESSION_EXPIRED' });
          void client.deactivate();
          return;
        }

        reconnectAttempt += 1;
        dispatch({ type: 'SOCKET_RECONNECTING', attempt: reconnectAttempt });

        if (!sessionCheckInFlight) {
          sessionCheckInFlight = true;
          void getSession()
            .catch((error) => {
              if (!disposed && isSessionExpiredSignal(error)) {
                terminal = true;
                dispatch({ type: 'SESSION_EXPIRED' });
                return client.deactivate();
              }
              return undefined;
            })
            .finally(() => {
              sessionCheckInFlight = false;
            });
        }
      },
    });

    stompClientRef.current = client;
    client.activate();

    return () => {
      disposed = true;
      unsubscribe();
      if (stompClientRef.current === client) {
        stompClientRef.current = null;
      }
      void client.deactivate();
    };
  }, [matchData?.matchId, matchData?.myRole]);

  const startMatch = useCallback(async () => {
    if (submissionLockedRef.current) return;

    submissionLockedRef.current = true;
    dispatch({ type: 'AI_START_REQUEST' });
    try {
      const response = await startLandGrabMatch();
      const { matchId, ...map } = response.data;
      dispatch({ type: 'AI_START_SUCCESS', matchId, map });
    } catch (error) {
      if (isSessionExpiredSignal(error)) {
        dispatch({ type: 'SESSION_EXPIRED' });
      } else {
        dispatch({
          type: 'RECOVERABLE_ERROR',
          phase: BATTLE_PHASE.INIT,
          message: errorMessage(error, '맵을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.'),
        });
      }
    } finally {
      submissionLockedRef.current = false;
    }
  }, []);

  const submitCode = useCallback(async ({ automatic = false } = {}) => {
    const snapshot = stateRef.current;
    if (
      submissionLockedRef.current
      || snapshot.phase !== BATTLE_PHASE.READY
      || !snapshot.matchId
    ) {
      return;
    }

    if (snapshot.mode === 'PvP') {
      const client = stompClientRef.current;
      if (!client?.connected || snapshot.connection !== CONNECTION_STATUS.CONNECTED) {
        dispatch({
          type: 'RECOVERABLE_ERROR',
          code: 'SOCKET_UNAVAILABLE',
          phase: BATTLE_PHASE.READY,
          title: '대전 서버에 다시 연결하고 있습니다',
          message: '연결이 복구되면 코드를 제출할 수 있습니다. 작성한 코드는 그대로 유지됩니다.',
        });
        return;
      }

      submissionLockedRef.current = true;
      dispatch({ type: 'SUBMIT_STARTED', automatic });
      try {
        client.publish({
          destination: '/app/game/submit',
          body: JSON.stringify({
            matchId: snapshot.matchId,
            code: snapshot.userCode,
            language: snapshot.language,
          }),
        });
      } catch (error) {
        submissionLockedRef.current = false;
        dispatch({
          type: 'RECOVERABLE_ERROR',
          code: 'SUBMIT_FAILED',
          phase: BATTLE_PHASE.READY,
          message: errorMessage(error, '코드를 전송하지 못했습니다. 연결 상태를 확인한 뒤 다시 제출해 주세요.'),
        });
      }
      return;
    }

    submissionLockedRef.current = true;
    dispatch({ type: 'SUBMIT_STARTED', automatic, busyLabel: 'COMPILING...' });
    try {
      const compileResponse = await compileLandGrabCode({
        matchId: snapshot.matchId,
        userCode: snapshot.userCode,
        language: snapshot.language,
      });

      if (compileResponse.data.status === 'error') {
        dispatch({
          type: 'MATCH_FINISHED',
          gameData: { p1_error: compileResponse.data.error },
        });
        return;
      }

      dispatch({ type: 'BUSY_LABEL_CHANGED', busyLabel: 'BATTLE...' });
      const runResponse = await runLandGrabMatch({
        matchId: snapshot.matchId,
        userCode: snapshot.userCode,
        language: snapshot.language,
        difficulty,
      });
      dispatch({ type: 'MATCH_FINISHED', gameData: runResponse.data });
    } catch (error) {
      if (isSessionExpiredSignal(error)) {
        dispatch({ type: 'SESSION_EXPIRED' });
      } else {
        dispatch({
          type: 'RECOVERABLE_ERROR',
          phase: BATTLE_PHASE.READY,
          message: errorMessage(error, '대전을 실행하지 못했습니다. 잠시 후 다시 제출해 주세요.'),
        });
      }
    } finally {
      submissionLockedRef.current = false;
    }
  }, [difficulty]);

  useEffect(() => {
    if (state.phase !== BATTLE_PHASE.READY) return undefined;

    if (state.timeLeft === 0) {
      if (state.mode === 'AI' || state.connection === CONNECTION_STATUS.CONNECTED) {
        void submitCode({ automatic: true });
      }
      return undefined;
    }

    const timer = window.setTimeout(() => dispatch({ type: 'TICK' }), 1_000);
    return () => window.clearTimeout(timer);
  }, [state.connection, state.mode, state.phase, state.timeLeft, submitCode]);

  return {
    ...state,
    isWaitingOpponent: state.phase === BATTLE_PHASE.SUBMITTED,
    setLanguage: (language) => dispatch({ type: 'SET_LANGUAGE', language }),
    setUserCode: (code) => dispatch({ type: 'SET_CODE', code }),
    startMatch,
    submitCode,
    showReplay: () => dispatch({ type: 'SHOW_REPLAY' }),
    clearNotice: () => dispatch({ type: 'CLEAR_NOTICE' }),
  };
};
