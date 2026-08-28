import React from 'react';
import Editor from '@monaco-editor/react';
import { BATTLE_PHASE } from '../battleSession';

const BattleEditor = ({
  borderColor,
  language,
  onCodeChange,
  onLanguageChange,
  opponentSubmitted,
  phase,
  resultOverlay,
  userCode,
}) => {
  const waiting = phase === BATTLE_PHASE.SUBMITTED;

  return (
    <div className="glass-panel" style={{ flex: 1, display: 'flex', flexDirection: 'column', padding: '0', overflow: 'hidden', minWidth: 0, borderColor }}>
      <div style={{ padding: '10px', borderBottom: '1px solid var(--border-color)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#0f1219' }}>
        <select
          aria-label="프로그래밍 언어"
          className="cyber-input"
          style={{ width: '150px', padding: '5px' }}
          value={language}
          onChange={(event) => onLanguageChange(event.target.value)}
          disabled={waiting}
        >
          <option value="python">Python 3.8</option>
          <option value="java">Java 17</option>
          <option value="cpp">C++ 17</option>
          <option value="c">C 11</option>
          <option value="javascript">Node.js</option>
        </select>
        <div style={{ fontSize: '12px', color: 'var(--text-dim)' }}>VS Code Style Editor</div>
      </div>

      {resultOverlay}

      <div style={{ flex: 1, position: 'relative' }}>
        <Editor
          height="100%"
          language={language === 'c' || language === 'cpp' ? 'cpp' : language}
          value={userCode}
          theme="vs-dark"
          onChange={onCodeChange}
          options={{
            minimap: { enabled: false },
            fontSize: 14,
            fontFamily: 'Fira Code',
            automaticLayout: true,
            readOnly: phase !== BATTLE_PHASE.READY,
          }}
        />
        {waiting && (
          <div style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(3px)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', zIndex: 10 }}>
            <div className="spinner" />
            <h3 style={{ marginTop: '20px', color: 'var(--primary)' }}>CODE SUBMITTED</h3>
            <p style={{ color: '#ccc' }}>
              {opponentSubmitted
                ? 'Both players ready! Processing match...'
                : 'Waiting for opponent to submit...'}
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

export default BattleEditor;
