import { render, screen } from '@testing-library/react';
import axios from 'axios';
import App from './App';

jest.mock('axios', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

beforeEach(() => {
  localStorage.clear();
  jest.clearAllMocks();
  window.history.replaceState({}, '', '/');
});

test('shows the lobby for an anonymous visitor', async () => {
  axios.get.mockRejectedValueOnce({ response: { status: 401 } });

  render(<App />);

  expect(await screen.findByText(/코드 크래쉬 아레나/i)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /로그인 \/ 게스트/i })).toBeInTheDocument();
});

test('restores a valid login session', async () => {
  axios.get.mockResolvedValueOnce({
    status: 200,
    data: { userId: 7, nickname: 'ArenaTester', role: 'USER' },
  });

  render(<App />);

  expect(await screen.findByText(/ArenaTester/i)).toBeInTheDocument();
  expect(localStorage.getItem('token')).toBeNull();
  expect(localStorage.getItem('userId')).toBeNull();
});
