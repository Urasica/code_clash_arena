import { getMatchOutcome } from './matchOutcome';

test('uses p1 as the player role for an AI victory', () => {
  expect(getMatchOutcome({ winner: 'p1' }, 'p1')).toEqual({
    title: 'VICTORY',
    color: 'var(--success)',
    reason: 'MATCH COMPLETED',
  });
});

test('shows a neutral draw outcome', () => {
  expect(getMatchOutcome({ winner: 'draw', reason: 'SCORE_DRAW' }, 'p2')).toEqual({
    title: 'DRAW',
    color: '#aaa',
    reason: 'SCORE_DRAW',
  });
});

test('treats an opponent disconnect as a victory', () => {
  expect(getMatchOutcome({ winner: null, reason: 'OPPONENT_DISCONNECTED' }, 'p2'))
    .toMatchObject({ title: 'VICTORY', reason: 'OPPONENT DISCONNECTED' });
});
