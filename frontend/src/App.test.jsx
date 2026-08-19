import { render, screen } from '@testing-library/react';
import { vi } from 'vitest';
import App from './App';
import { getSession } from './features/auth/authApi';

vi.mock('./features/auth/authApi', () => ({
  getSession: vi.fn(),
  logout: vi.fn(),
}));

beforeEach(() => {
  localStorage.clear();
  vi.clearAllMocks();
  window.history.replaceState({}, '', '/');
});

test('shows the lobby for an anonymous visitor', async () => {
  getSession.mockRejectedValueOnce({ response: { status: 401 } });

  render(<App />);

  expect(await screen.findByText(/코드 크래쉬 아레나/i)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /로그인 \/ 게스트/i })).toBeInTheDocument();
});

test('restores a valid login session', async () => {
  getSession.mockResolvedValueOnce({
    status: 200,
    data: { userId: 7, nickname: 'ArenaTester', role: 'USER' },
  });

  render(<App />);

  expect(await screen.findByText(/ArenaTester/i)).toBeInTheDocument();
  expect(localStorage.getItem('token')).toBeNull();
  expect(localStorage.getItem('userId')).toBeNull();
});

test('shows a stable OAuth cancellation message and removes the redirect code', async () => {
  window.history.replaceState({}, '', '/?authError=OAUTH_CANCELLED&source=test');
  getSession.mockRejectedValueOnce({ response: { status: 401 } });

  render(<App />);

  expect(await screen.findByRole('alert')).toHaveTextContent('Google 로그인이 취소되었습니다.');
  expect(window.location.search).toBe('?source=test');
});
