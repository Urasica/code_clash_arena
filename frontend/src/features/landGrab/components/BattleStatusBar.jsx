import React from 'react';
import {
  BATTLE_PHASE,
  CONNECTION_STATUS,
  formatBattleTime,
} from '../battleSession';

const roleColor = (role) => (
  role === 'p1' ? '#00f0ff' : (role === 'p2' ? '#ff0055' : '#888')
);

const opponentColor = (role) => (
  role === 'p1' ? '#ff0055' : (role === 'p2' ? '#00f0ff' : '#888')
);

const connectionLabel = {
  [CONNECTION_STATUS.CONNECTING]: 'CONNECTING',
  [CONNECTION_STATUS.CONNECTED]: 'ONLINE',
  [CONNECTION_STATUS.RECONNECTING]: 'RECONNECTING',
  [CONNECTION_STATUS.FAILED]: 'OFFLINE',
};

const BattleStatusBar = ({
  busyLabel,
  connection,
  difficulty,
  mode,
  myRole,
  onExit,
  onGenerate,
  onRetry,
  onSubmit,
  opponentSubmitted,
  phase,
  timeLeft,
}) => {
  const waiting = phase === BATTLE_PHASE.SUBMITTED;
  const finished = [BATTLE_PHASE.FINISHED, BATTLE_PHASE.REPLAY].includes(phase);
  const canSubmit = phase === BATTLE_PHASE.READY
    && !busyLabel
    && (mode !== 'PvP' || connection === CONNECTION_STATUS.CONNECTED);

  return (
    <header className="glass-panel" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', padding: '10px 20px', flexShrink: 0, position: 'relative' }}>
      <div style={{ display: 'flex', gap: '20px', flex: 1, alignItems: 'center' }}>
        <button className="cyber-button secondary" style={{ fontSize: '12px', padding: '5px 10px' }} onClick={onExit}>◀ EXIT</button>
        <h2 style={{ margin: 0, color: 'var(--primary)', fontFamily: 'Orbitron' }}>
          ARENA 01 <span style={{ fontSize: '14px', color: '#888' }}>
            [{mode === 'PvP' ? 'PVP MATCH' : difficulty.toUpperCase()}]
          </span>
        </h2>
        {mode === 'PvP' && (
          <span
            aria-label={`실시간 연결 상태: ${connectionLabel[connection] || connection}`}
            style={{
              color: connection === CONNECTION_STATUS.CONNECTED ? 'var(--success)' : '#ffb347',
              fontFamily: 'Orbitron',
              fontSize: '10px',
              letterSpacing: '1px',
            }}
          >
            ● {connectionLabel[connection] || connection.toUpperCase()}
          </span>
        )}
      </div>

      {mode === 'PvP' && (
        <div style={{ position: 'absolute', left: '50%', transform: 'translateX(-50%)', display: 'flex', gap: '30px', alignItems: 'center' }}>
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: '12px', color: '#888' }}>YOU ({myRole ? myRole.toUpperCase() : '?'})</div>
            <div style={{ color: finished ? '#888' : (waiting ? 'var(--success)' : roleColor(myRole)), fontWeight: 'bold' }}>
              {finished ? 'FINISHED' : (waiting ? 'READY' : 'CODING')}
            </div>
          </div>
          <div style={{ fontSize: '20px', color: '#444', fontFamily: 'Orbitron' }}>VS</div>
          <div style={{ textAlign: 'left' }}>
            <div style={{ fontSize: '12px', color: '#888' }}>OPPONENT</div>
            <div style={{ color: finished ? '#888' : (opponentSubmitted ? 'var(--success)' : opponentColor(myRole)), fontWeight: 'bold' }}>
              {finished ? 'FINISHED' : (opponentSubmitted ? 'READY' : 'CODING')}
            </div>
          </div>
        </div>
      )}

      <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
        {(phase === BATTLE_PHASE.READY || waiting) && (
          <div style={{ fontSize: '20px', fontFamily: 'Orbitron', color: timeLeft < 60 ? 'var(--danger)' : '#fff', textShadow: '0 0 5px rgba(255,255,255,0.3)', marginRight: '10px' }}>
            ⏱ {formatBattleTime(timeLeft)}
          </div>
        )}

        <div style={{ display: 'flex', gap: '10px' }}>
          {mode === 'AI' && phase === BATTLE_PHASE.INIT && (
            <button className="cyber-button" onClick={onGenerate} disabled={Boolean(busyLabel)}>
              {busyLabel || 'GENERATE MAP'}
            </button>
          )}
          {phase === BATTLE_PHASE.READY && (
            <button className="cyber-button" onClick={onSubmit} disabled={!canSubmit}>
              {busyLabel || (connection === CONNECTION_STATUS.RECONNECTING ? 'RECONNECTING...' : '🚀 SUBMIT CODE')}
            </button>
          )}
          {phase === BATTLE_PHASE.SUBMITTED && (
            <button className="cyber-button" disabled style={{ background: '#333' }}>⏳ WAITING...</button>
          )}
          {phase === BATTLE_PHASE.FINISHED && (
            <button
              className="cyber-button"
              onClick={mode === 'PvP' ? onExit : onRetry}
              style={{ borderColor: mode === 'PvP' ? 'var(--secondary)' : undefined, color: mode === 'PvP' ? 'var(--secondary)' : undefined }}
            >
              {mode === 'PvP' ? '◀ BACK TO LOBBY' : '🔄 RETRY'}
            </button>
          )}
        </div>
      </div>
    </header>
  );
};

export default BattleStatusBar;
