const VICTORY_COLOR = 'var(--success)';
const DEFEAT_COLOR = 'var(--danger)';

export const getMatchOutcome = (gameData, playerRole) => {
  if (gameData.reason === 'OPPONENT_DISCONNECTED') {
    return {
      title: 'VICTORY',
      color: VICTORY_COLOR,
      reason: 'OPPONENT DISCONNECTED',
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

  const reason = gameData.p1_error || gameData.p2_error
    ? 'RUNTIME ERROR'
    : gameData.reason || 'MATCH COMPLETED';

  return { title, color, reason };
};
