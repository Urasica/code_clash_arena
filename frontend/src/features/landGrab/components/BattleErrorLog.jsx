import React from 'react';

const BattleErrorLog = ({ gameData }) => {
  const error = gameData?.p1_error || gameData?.p2_error;
  if (!error) return null;

  const title = gameData.p1_error
    ? '❌ COMPILATION / RUNTIME ERROR (PLAYER 1)'
    : '❌ SYSTEM ERROR (PLAYER 2)';

  return (
    <div style={{ minHeight: '250px', background: '#1e1e1e', borderTop: '2px solid var(--danger)', display: 'flex', flexDirection: 'column', margin: '0 20px', boxShadow: '0 -5px 20px rgba(255, 0, 0, 0.1)' }}>
      <div style={{ background: 'var(--danger)', color: '#000', padding: '8px 20px', fontSize: '13px', fontWeight: 'bold', fontFamily: 'monospace', display: 'flex', alignItems: 'center', gap: '10px' }}>
        <span>{title}</span>
        <span style={{ marginLeft: 'auto', fontSize: '11px', opacity: 0.8 }}>SCROLL DOWN TO SEE DETAILS</span>
      </div>
      <div style={{ flex: 1, padding: '15px 20px', fontFamily: 'Consolas, monospace', fontSize: '14px', color: '#ff8b8b', whiteSpace: 'pre-wrap', lineHeight: '1.6' }}>
        {error}
      </div>
    </div>
  );
};

export default BattleErrorLog;
