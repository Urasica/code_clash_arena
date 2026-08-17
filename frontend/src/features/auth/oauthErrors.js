const OAUTH_ERROR_MESSAGES = {
  OAUTH_CANCELLED: 'Google 로그인이 취소되었습니다.',
  OAUTH_CLAIMS_INVALID: 'Google 계정 정보를 확인할 수 없습니다.',
  OAUTH_EMAIL_UNVERIFIED: '인증된 Google 이메일이 필요합니다.',
  OAUTH_ACCOUNT_CONFLICT: '이미 존재하는 계정과 충돌했습니다. 다른 로그인 방법을 사용해 주세요.',
  OAUTH_FAILED: 'Google 로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.',
};

export const consumeOAuthError = () => {
  const url = new URL(window.location.href);
  const code = url.searchParams.get('authError');
  if (!code) {
    return null;
  }

  url.searchParams.delete('authError');
  window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`);
  return OAUTH_ERROR_MESSAGES[code] || OAUTH_ERROR_MESSAGES.OAUTH_FAILED;
};
