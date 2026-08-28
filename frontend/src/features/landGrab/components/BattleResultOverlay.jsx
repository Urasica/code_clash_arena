import React from 'react';
import { getMatchOutcome } from '../matchOutcome';

const BattleResultOverlay = ({ gameData, mode, myRole, onBack, onReplay }) => {
  if (!gameData) return null;

  const playerRole = mode === 'AI' ? 'p1' : myRole;
  const { title, color, reason } = getMatchOutcome(gameData, playerRole);
  const error = gameData.p1_error || gameData.p2_error;

  return (
    <div style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', background: 'rgba(0,0,0,0.85)', backdropFilter: 'blur(5px)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', zIndex: 100, animation: 'fadeIn 0.5s' }}>
      <h1 style={{ fontSize: '80px', color, margin: 0, fontFamily: 'Orbitron', textShadow: `0 0 30px ${color}`, letterSpacing: '5px' }}>
        {title}
      </h1>
      <h3 style={{ color: '#ccc', marginTop: '10px', fontSize: '20px', fontFamily: 'Orbitron' }}>
        {reason}
      </h3>

      {error && (
        <div style={{ background: 'rgba(50, 0, 0, 0.8)', border: '1px solid red', padding: '20px', marginTop: '30px', maxWidth: '600px', borderRadius: '4px' }}>
          <div style={{ color: 'red', fontWeight: 'bold', marginBottom: '10px' }}>🛑 ERROR LOG:</div>
          <div style={{ color: '#ffcccc', fontSize: '13px', whiteSpace: 'pre-wrap', fontFamily: 'monospace' }}>{error}</div>
        </div>
      )}

      <div style={{ marginTop: '50px', display: 'flex', gap: '20px' }}>
        <button className="cyber-button" onClick={onBack} style={{ padding: '15px 40px', fontSize: '18px' }}>
          BACK TO LOBBY
        </button>
        {gameData.logs?.length > 0 && (
          <button className="cyber-button secondary" onClick={onReplay} style={{ padding: '15px 40px', fontSize: '18px' }}>
            WATCH CODE
          </button>
        )}
      </div>
    </div>
  );
};

export default BattleResultOverlay;
