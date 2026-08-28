import React from 'react';
import BattleEditor from './features/landGrab/components/BattleEditor';
import BattleErrorLog from './features/landGrab/components/BattleErrorLog';
import BattleMissionPanel from './features/landGrab/components/BattleMissionPanel';
import BattleNotice from './features/landGrab/components/BattleNotice';
import BattleReplayPanel from './features/landGrab/components/BattleReplayPanel';
import BattleResultOverlay from './features/landGrab/components/BattleResultOverlay';
import BattleStatusBar from './features/landGrab/components/BattleStatusBar';
import { BATTLE_PHASE } from './features/landGrab/battleSession';
import { useBattleSession } from './features/landGrab/useBattleSession';

const playerColor = (role) => (
  role === 'p1' ? '#00f0ff' : (role === 'p2' ? '#ff0055' : '#888')
);

const GameArena = ({ onBack, difficulty, matchData }) => {
  const battle = useBattleSession({ difficulty, matchData });
  const error = battle.gameData?.p1_error || battle.gameData?.p2_error;

  return (
    <div className="cyber-container" style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', paddingBottom: '20px', position: 'relative' }}>
      <BattleStatusBar
        busyLabel={battle.busyLabel}
        connection={battle.connection}
        difficulty={difficulty}
        mode={battle.mode}
        myRole={battle.myRole}
        onExit={onBack}
        onGenerate={battle.startMatch}
        onRetry={battle.startMatch}
        onSubmit={battle.submitCode}
        opponentSubmitted={battle.opponentSubmitted}
        phase={battle.phase}
        timeLeft={battle.timeLeft}
      />

      <BattleNotice
        notice={battle.notice}
        onClear={battle.clearNotice}
        onSessionExpired={onBack}
      />

      <div style={{ display: 'flex', gap: '20px', height: '80vh', marginBottom: error ? '20px' : '0' }}>
        <BattleEditor
          borderColor={battle.mode === 'PvP' ? playerColor(battle.myRole) : undefined}
          language={battle.language}
          onCodeChange={battle.setUserCode}
          onLanguageChange={battle.setLanguage}
          opponentSubmitted={battle.opponentSubmitted}
          phase={battle.phase}
          resultOverlay={battle.phase === BATTLE_PHASE.FINISHED ? (
            <BattleResultOverlay
              gameData={battle.gameData}
              mode={battle.mode}
              myRole={battle.myRole}
              onBack={onBack}
              onReplay={battle.showReplay}
            />
          ) : null}
          userCode={battle.userCode}
        />

        <div style={{ width: '500px', display: 'flex', flexDirection: 'column', gap: '20px', minWidth: 0 }}>
          <BattleReplayPanel
            gameData={battle.gameData}
            isWaitingOpponent={battle.isWaitingOpponent}
            mode={battle.mode}
          />
          <BattleMissionPanel gameData={battle.gameData} mode={battle.mode} />
        </div>
      </div>

      <BattleErrorLog gameData={battle.gameData} />
    </div>
  );
};

export default GameArena;
