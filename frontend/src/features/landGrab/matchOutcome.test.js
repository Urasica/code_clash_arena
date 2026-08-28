import { getMatchOutcome } from './matchOutcome';

test('uses p1 as the player role for an AI victory', () => {
  expect(getMatchOutcome({ winner: 'p1' }, 'p1')).toEqual({
    title: 'VICTORY',
    color: 'var(--success)',
    reason: 'MATCH_COMPLETED',
  });
});

test('shows a neutral draw outcome', () => {
  expect(getMatchOutcome({ winner: 'draw', reason: 'SCORE_DRAW' }, 'p2')).toEqual({
    title: 'DRAW',
    color: '#aaa',
    reason: 'SCORE_DRAW',
  });
});

test('uses the recorded winner for both sides of a disconnect', () => {
  const result = { winner: 'p2', reason: 'OPPONENT_DISCONNECTED' };

  expect(getMatchOutcome(result, 'p2'))
    .toMatchObject({ title: 'VICTORY', reason: 'OPPONENT_DISCONNECTED' });
  expect(getMatchOutcome(result, 'p1'))
    .toMatchObject({ title: 'DEFEAT', reason: 'OPPONENT_DISCONNECTED' });
});

test('shows the canonical crash reason used by persistence', () => {
  expect(getMatchOutcome({
    winner: 'p2',
    reason: 'PLAYER_CRASH',
    p1_error: 'timed out',
  }, 'p1')).toMatchObject({
    title: 'DEFEAT',
    reason: 'PLAYER_CRASH',
  });
});
