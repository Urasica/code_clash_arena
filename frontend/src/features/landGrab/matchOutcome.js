const VICTORY_COLOR = 'var(--success)';
const DEFEAT_COLOR = 'var(--danger)';

export const getMatchOutcome = (gameData, playerRole) => {
  if (gameData.reason === 'OPPONENT_DISCONNECTED') {
    const wonByDisconnect = gameData.winner == null || gameData.winner === playerRole;
    return {
      title: wonByDisconnect ? 'VICTORY' : 'DEFEAT',
      color: wonByDisconnect ? VICTORY_COLOR : DEFEAT_COLOR,
      reason: gameData.reason,
    };
  }

  let title = 'DEFEAT';
  let color = DEFEAT_COLOR;

  if (gameData.winner === playerRole) {
    title = 'VICTORY';
    color = VICTORY_COLOR;
  } else if (gameData.winner === 'draw') {
    title = 'DRAW';
    color = '#aaa';
  }

  const reason = gameData.reason
    || (gameData.p1_error || gameData.p2_error ? 'RUNTIME_ERROR' : 'MATCH_COMPLETED');

  return { title, color, reason };
};
