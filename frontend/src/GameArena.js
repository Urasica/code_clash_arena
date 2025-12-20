import React, { useState, useEffect } from 'react';
import axios from 'axios';
import ReplayViewer from './ReplayViewer';
import Editor from "@monaco-editor/react"; 
import { TEMPLATES } from './CodeTemplates';

const GameArena = ({ onBack }) => {
  const [matchId, setMatchId] = useState(null);
  const [gameData, setGameData] = useState(null);
  const [status, setStatus] = useState('init'); // init, ready, running, finished
  const [loading, setLoading] = useState(false);
  
  // 기능 상태
  const [language, setLanguage] = useState('python');
  const [userCode, setUserCode] = useState(TEMPLATES['python']);
  const [timeLeft, setTimeLeft] = useState(600); // 10분 (600초)

  // 타이머 로직
  useEffect(() => {
    let timer;
    if (status === 'ready' && timeLeft > 0) {
      timer = setInterval(() => {
        setTimeLeft((prev) => prev - 1);
      }, 1000);
    } else if (timeLeft === 0 && status === 'ready') {
      handleRunMatch(); 
      alert("⏰ Time Over! Code Auto-Submitted.");
    }
    return () => clearInterval(timer);
  }, [status, timeLeft]);

  // 시간 포맷
  const formatTime = (seconds) => {
    const m = Math.floor(seconds / 60).toString().padStart(2, '0');
    const s = (seconds % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  };

  // 1. 매치 시작
  const handleStartMatch = async () => {
    setLoading(true);
    try {
      const res = await axios.post('http://localhost:8080/api/match/start');
      const { matchId, map } = res.data;
      setMatchId(matchId);

      // 프리뷰 데이터 생성
      const boardSize = 15;
      const initialBoard = Array(boardSize).fill(0).map(() => Array(boardSize).fill(0));
      initialBoard[0][0] = 1; initialBoard[boardSize-1][boardSize-1] = 2;
      
      setGameData({
        logs: [{
          turn: 0, board_size: boardSize, walls: map.walls, coins: map.coins,
          p1: { pos: [0, 0] }, p2: { pos: [boardSize-1, boardSize-1] }, board: initialBoard
        }]
      });
      setStatus('ready');
      setTimeLeft(600);
    } catch (err) {
      alert("Error: " + err.message);
    }
    setLoading(false);
  };

  // 2. 코드 실행
  const handleRunMatch = async () => {
    if (!matchId) return;
    setLoading(true);
    try {
      const res = await axios.post('http://localhost:8080/api/match/run', {
        matchId: matchId,
        userCode: userCode
      });
      setGameData(res.data);
      setStatus('finished');
    } catch (err) {
      alert("Execution Error: " + err.message);
    }
    setLoading(false);
  };

  return (
    <div className="cyber-container" style={{ height: '95vh', display: 'flex', flexDirection: 'column' }}>
      {/* 헤더 */}
      <header className="glass-panel" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', padding: '10px 20px', flexShrink: 0 }}>
        <div style={{ display: 'flex', gap: '20px', flex: 1 }}>
          <button className="cyber-button secondary" style={{ fontSize: '12px', padding: '5px 10px' }} onClick={onBack}>◀ EXIT</button>
          <h2 style={{ margin: 0, color: 'var(--primary)' }}>ARENA 01</h2>
        </div>

        <div style={{ width: '500px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <div style={{ fontSize: '24px', fontFamily: 'Orbitron', color: timeLeft < 60 ? 'var(--danger)' : 'white' }}>
            {status === 'ready' ? `TIME: ${formatTime(timeLeft)}` : 'READY'}
          </div>
          
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
             {status === 'init' && (
                <button className="cyber-button" onClick={handleStartMatch} disabled={loading}>GENERATE MAP</button>
              )}
              {status === 'ready' && (
                 <button className="cyber-button" onClick={handleRunMatch} disabled={loading}>🚀 SUBMIT CODE</button>
              )}
              {status === 'finished' && (
                 <button className="cyber-button" onClick={handleStartMatch}>🔄 RETRY</button>
              )}
          </div>
        </div>
      </header>

      {/* 메인 컨텐츠 (좌우 분할) */}
      <div style={{ display: 'flex', gap: '20px', flex: 1, minHeight: 0 }}>
        
        {/* [왼쪽] 코드 에디터 영역 */}
        <div className="glass-panel" style={{ 
            flex: 1, 
            display: 'flex', 
            flexDirection: 'column', 
            padding: '0', 
            overflow: 'hidden', // [중요] 자식 요소(Editor)가 튀어나가는 것 방지
            minWidth: 0         // [중요] Flex 아이템이 내용물보다 작아질 수 있게 허용
        }}>
          {/* 툴바 */}
          <div style={{ padding: '10px', borderBottom: '1px solid var(--border-color)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#0f1219' }}>
            
            {/* [수정] 언어 선택 드롭다운 확장 */}
            <select 
              className="cyber-input" 
              style={{ width: '150px', padding: '5px' }} 
              value={language} 
              onChange={(e) => {
                const newLang = e.target.value;
                setLanguage(newLang);
                setUserCode(TEMPLATES[newLang]); // 선택한 언어 템플릿 로드
              }}
            >
              <option value="python">Python 3.8</option>
              <option value="java">Java 17</option>
              <option value="cpp">C++ 17</option>
              <option value="c">C 11</option>
              <option value="javascript">Node.js</option>
            </select>
            
            <div style={{ fontSize: '12px', color: 'var(--text-dim)' }}>VS Code Style Editor</div>
          </div>

          {/* Monaco Editor */}
          <div style={{ flex: 1, position: 'relative' }}>
             <Editor
               height="100%"
               language={language === 'c' || language === 'cpp' ? 'cpp' : language} // Monaco 언어 설정 매핑
               value={userCode}
               theme="vs-dark"
               onChange={(value) => setUserCode(value)}
               options={{
                 minimap: { enabled: false },
                 fontSize: 14,
                 fontFamily: 'Fira Code',
                 scrollBeyondLastLine: false,
                 automaticLayout: true,
                 readOnly: status !== 'ready',
               }}
             />
          </div>
          
          {/* 에러 로그 창 */}
          {gameData?.p1_error && (
            <div style={{ height: '100px', padding: '10px', background: '#2d0a0a', color: '#ff8080', borderTop: '1px solid var(--danger)', overflowY: 'auto', fontSize: '12px' }}>
              <strong>[RUNTIME ERROR]</strong><br/>
              <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{gameData.p1_error}</pre>
            </div>
          )}
        </div>

        {/* [오른쪽] 리플레이 뷰어 및 정보 */}
        <div style={{ 
            width: '500px', 
            display: 'flex', 
            flexDirection: 'column', 
            gap: '20px',
            minWidth: 0 // [중요] Flex 축소 허용
        }}>
          
          {/* 뷰어 패널 */}
          <div className="glass-panel" style={{ 
              flex: 1, 
              display: 'flex', 
              flexDirection: 'column', 
              justifyContent: 'center', 
              alignItems: 'center', 
              background: '#000',
              padding: '20px',
              minHeight: 0 // [중요] 세로 방향으로도 넘치지 않도록
          }}>
            {gameData ? (
              <ReplayViewer gameData={gameData} />
            ) : (
              <div style={{ color: '#555', fontFamily: 'Orbitron', textAlign: 'center' }}>
                <div style={{fontSize: '40px', marginBottom: '10px'}}>📡</div>
                AWAITING SIGNAL ...
              </div>
            )}
          </div>

          {/* 게임 규칙 요약 */}
          <div className="glass-panel" style={{ height: '200px', minHeight: '200px', overflowY: 'auto' }}>
            <h3 style={{ marginTop: 0, color: 'var(--primary)', fontSize: '16px' }}>📝 MISSION OBJECTIVES</h3>
            <ul style={{ fontSize: '13px', lineHeight: '1.6', paddingLeft: '20px', color: '#ccc' }}>
              <li><strong>맵 크기:</strong> 15x15 Grid</li>
              <li><strong>시간 제한:</strong> 10분</li>
              <li><strong>목표:</strong> AI보다 높은 점수를 획득하세요!</li>
              <li><span style={{ color: 'gold' }}>● Coin:</span> +5 점 (3개 이하면 재생성)</li>
              <li><span style={{ color: '#00f0ff' }}>■ 영역:</span> 타일당 +1 점.</li>
              <li><strong>스틸:</strong> 적의 영역을 밟으면 당신의 영역으로 전환됩니다.</li>
            </ul>
            {gameData?.winner && (
                <div style={{ marginTop: '10px', padding: '10px', border: '1px solid white', textAlign: 'center', background: gameData.winner === 'p1' ? 'var(--primary)' : 'var(--danger)', color: 'black', fontWeight: 'bold' }}>
                    RESULT: {gameData.winner.toUpperCase()} WIN!
                </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default GameArena;