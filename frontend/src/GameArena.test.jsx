import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import GameArena from './GameArena';
import {
  compileLandGrabCode,
  runLandGrabMatch,
  startLandGrabMatch,
} from './features/landGrab/landGrabApi';

vi.mock('@monaco-editor/react', () => ({
  default: ({ value, onChange }) => (
    <textarea aria-label="코드 편집기" value={value} onChange={(event) => onChange(event.target.value)} />
  ),
}));

vi.mock('./ReplayViewer', () => ({
  default: () => <div>replay viewer</div>,
}));

vi.mock('./features/landGrab/landGrabApi', () => ({
  startLandGrabMatch: vi.fn(),
  compileLandGrabCode: vi.fn(),
  runLandGrabMatch: vi.fn(),
}));

beforeEach(() => {
  vi.clearAllMocks();
});

test('completes the existing AI generate, compile, run, and result flow', async () => {
  startLandGrabMatch.mockResolvedValueOnce({
    data: { matchId: 'ai-7', walls: [], coins: [] },
  });
  compileLandGrabCode.mockResolvedValueOnce({ data: { status: 'ok' } });
  runLandGrabMatch.mockResolvedValueOnce({
    data: { winner: 'p1', logs: [{ turn: 0 }] },
  });

  render(<GameArena difficulty="easy" matchData={null} onBack={vi.fn()} />);
  fireEvent.click(screen.getByRole('button', { name: 'GENERATE MAP' }));
  const submit = await screen.findByRole('button', { name: /SUBMIT CODE/ });
  fireEvent.click(submit);

  expect(await screen.findByRole('heading', { name: 'VICTORY' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'BACK TO LOBBY' })).toBeInTheDocument();
  expect(compileLandGrabCode).toHaveBeenCalledOnce();
  expect(runLandGrabMatch).toHaveBeenCalledWith(expect.objectContaining({
    matchId: 'ai-7',
    difficulty: 'easy',
  }));
});

test('shows an actionable session-expiry panel instead of a browser alert', async () => {
  const onBack = vi.fn();
  startLandGrabMatch.mockRejectedValueOnce({ response: { status: 401 } });

  render(<GameArena difficulty="normal" matchData={null} onBack={onBack} />);
  fireEvent.click(screen.getByRole('button', { name: 'GENERATE MAP' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('로그인 세션이 만료되었습니다');
  fireEvent.click(screen.getByRole('button', { name: '로비로 돌아가기' }));
  await waitFor(() => expect(onBack).toHaveBeenCalledOnce());
});
