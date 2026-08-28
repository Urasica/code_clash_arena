import React from 'react';

const colors = {
  error: 'var(--danger)',
  warning: '#ffb347',
  info: 'var(--primary)',
};

const BattleNotice = ({
  notice,
  onClear,
  onSessionExpired,
  sessionActionLabel = '로비로 돌아가기',
}) => {
  if (!notice) return null;

  const color = colors[notice.level] || colors.info;
  const expired = notice.code === 'SESSION_EXPIRED';

  return (
    <div
      className="glass-panel"
      role="alert"
      style={{
        borderColor: color,
        color: '#ddd',
        display: 'flex',
        alignItems: 'center',
        gap: '16px',
        marginBottom: '20px',
        padding: '12px 18px',
      }}
    >
      <div style={{ flex: 1 }}>
        <strong style={{ color, display: 'block', marginBottom: '4px' }}>{notice.title}</strong>
        <span style={{ fontSize: '13px' }}>{notice.message}</span>
      </div>
      {expired ? (
        <button className="cyber-button" onClick={onSessionExpired}>{sessionActionLabel}</button>
      ) : (
        notice.level !== 'warning' && (
          <button className="cyber-button secondary" onClick={onClear} aria-label="안내 닫기">닫기</button>
        )
      )}
    </div>
  );
};

export default BattleNotice;
