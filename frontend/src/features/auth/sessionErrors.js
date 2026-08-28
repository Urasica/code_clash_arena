const SESSION_ERROR_PATTERN = /unauthori[sz]ed|not authenticated|authentication|access denied|forbidden|session\s*(?:has\s*)?expired|expired\s*session|invalid\s*(?:jwt|token)/i;

export const sessionSignalText = (value) => [
  value?.headers?.message,
  value?.body,
  value?.reason,
  value?.message,
  value?.response?.data?.error,
].filter(Boolean).join(' ');

export const isSessionExpiredSignal = (value) => {
  const status = value?.response?.status;
  const closeCode = value?.code;
  return status === 401
    || closeCode === 4401
    || closeCode === 4403
    || SESSION_ERROR_PATTERN.test(sessionSignalText(value));
};
