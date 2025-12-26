import React, { useState, useEffect } from 'react';
import axios from 'axios';
import Editor from "@monaco-editor/react"; 
import ReplayViewer from './ReplayViewer';
import { TEMPLATES } from './CodeTemplates';

const GameArena = ({ onBack, difficulty }) => {
  const [matchId, setMatchId] = useState(null);
  const [gameData, setGameData] = useState(null);
  const [status, setStatus] = useState('init'); // init, ready, running, finished
  const [loading, setLoading] = useState(false);
  
  // 기능 상태
  const [language, setLanguage] = useState('python');
  const [userCode, setUserCode] = useState(TEMPLATES['python']);
  const [timeLeft, setTimeLeft] = useState(600); // 10분

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

  // 1. 매치 시작 (맵 생성)
  const handleStartMatch = async () => {
    setLoading(true);
    try {
      const res = await axios.post(
        'http://localhost:8080/api/match/land-grab/start', 
        {},
        { withCredentials: true }
      );
      const { matchId, map } = res.data;
      setMatchId(matchId);

      // 프리뷰 데이터 생성 (Turn 0)
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
      // [수정] 401 에러(인증 실패) 처리
      if (err.response && err.response.status === 401) {
        alert("세션이 만료되었습니다. 다시 로그인해주세요.");
        onBack(); // 로비로 튕겨내기
      } else {
        alert("Error: " + (err.response?.data?.error || err.message));
      }
    }
    setLoading(false);
  };

  // 2. 코드 실행
  const handleRunMatch = async () => {
    if (!matchId) return;
    setGameData(null); // 초기화
    
    try {
      // --- [Step 1] 컴파일 검증 ---
      setLoading('COMPILING...'); // 버튼 텍스트 변경용 (state 수정 필요시 string으로 변경 추천)
      
      const compileRes = await axios.post('http://localhost:8080/api/match/land-grab/compile', {
        matchId: matchId,
        userCode: userCode,
        language: language
      },
      { withCredentials: true }
      );

      // 컴파일 에러가 있으면 여기서 중단
      if (compileRes.data.status === 'error') {
        setGameData({ p1_error: compileRes.data.error }); // 에러 박스 표시
        setLoading(false);
        return; // 🚫 제출 중단
      }

      // --- [Step 2] 실제 게임 실행 (제출) ---
      setLoading('BATTLE...');
      
      const runRes = await axios.post('http://localhost:8080/api/match/land-grab/run', {
        matchId: matchId,
        userCode: userCode,
        language: language,
        difficulty: difficulty
      },
      { withCredentials: true }
      );
      
      setGameData(runRes.data);
      setStatus('finished');

    } catch (err) {
      alert("System Error: " + err.message);
    }
    setLoading(false);
  };

  // 에러 로그 추출
  const errorLog = gameData?.p1_error || gameData?.p2_error;
  const errorTitle = gameData?.p1_error 
    ? "❌ COMPILATION / RUNTIME ERROR (PLAYER 1)" 
    : "❌ SYSTEM ERROR (PLAYER 2)";

  return (
    // [수정] height: '95vh' 제거 -> minHeight: '100vh'로 변경하여 스크롤 허용
    <div className="cyber-container" style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', paddingBottom: '20px' }}>
      
      {/* 1. Header Area */}
      <header className="glass-panel" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', padding: '10px 20px', flexShrink: 0 }}>
        <div style={{ display: 'flex', gap: '20px', flex: 1 }}>
          <button className="cyber-button secondary" style={{ fontSize: '12px', padding: '5px 10px' }} onClick={onBack}>◀ EXIT</button>
          <h2 style={{ margin: 0, color: 'var(--primary)', fontFamily: 'Orbitron' }}>
            ARENA 01 <span style={{ fontSize: '14px', color: '#888' }}>[{difficulty.toUpperCase()}]</span>
          </h2>
        </div>

        {/* [수정] 타이머와 버튼을 한 그룹으로 묶음 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
          
          {/* 타이머 표시 (Ready 상태일 때만) */}
          {status === 'ready' && (
            <div style={{ 
              fontSize: '20px', fontFamily: 'Orbitron', 
              color: timeLeft < 60 ? 'var(--danger)' : '#fff',
              textShadow: '0 0 5px rgba(255,255,255,0.3)',
              marginRight: '10px'
            }}>
              ⏱ {formatTime(timeLeft)}
            </div>
          )}
          
          <div style={{ display: 'flex', gap: '10px' }}>
             {status === 'init' && (
                <button className="cyber-button" onClick={handleStartMatch} disabled={loading}>GENERATE MAP</button>
              )}
              {status === 'ready' && (
                 <button className="cyber-button" onClick={handleRunMatch} disabled={loading}>
                  {loading === true ? 'PROCESSING...' : (loading || '🚀 SUBMIT CODE')} 
                </button>
              )}
              {status === 'finished' && (
                 <button className="cyber-button" onClick={handleStartMatch}>🔄 RETRY</button>
              )}
          </div>
        </div>
      </header>

      {/* 2. Main Content (Split View) */}
      {/* [수정] flex: 1 대신 height: '80vh' 고정하여 에러 박스 생성 시 찌그러짐 방지 */}
      <div style={{ display: 'flex', gap: '20px', height: '80vh', marginBottom: errorLog ? '20px' : '0' }}>
        
        {/* [Left] Code Editor */}
        <div className="glass-panel" style={{ 
            flex: 1, display: 'flex', flexDirection: 'column', padding: '0', 
            overflow: 'hidden', minWidth: 0 
        }}>
          {/* Editor Toolbar */}
          <div style={{ padding: '10px', borderBottom: '1px solid var(--border-color)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#0f1219' }}>
            <select 
              className="cyber-input" 
              style={{ width: '150px', padding: '5px' }} 
              value={language} 
              onChange={(e) => {
                const newLang = e.target.value;
                setLanguage(newLang);
                setUserCode(TEMPLATES[newLang] || "");
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
               language={language === 'c' || language === 'cpp' ? 'cpp' : language}
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
        </div>

        {/* [Right] Replay Viewer & Info */}
        <div style={{ width: '500px', display: 'flex', flexDirection: 'column', gap: '20px', minWidth: 0 }}>
          
          {/* Viewer Panel */}
          <div className="glass-panel" style={{ 
              flex: 1, display: 'flex', flexDirection: 'column', 
              justifyContent: 'center', alignItems: 'center', 
              background: '#000', padding: '20px', minHeight: 0 
          }}>
            {gameData ? (
              <ReplayViewer gameData={gameData} />
            ) : (
              <div style={{ color: '#555', fontFamily: 'Orbitron', textAlign: 'center' }}>
                <div style={{fontSize: '40px', marginBottom: '10px'}}>📡</div>
                맵 생성을 누르면 시작합니다...
              </div>
            )}
          </div>

          {/* Mission Objectives */}
          <div className="glass-panel" style={{ height: '200px', minHeight: '200px', overflowY: 'auto' }}>
            <h3 style={{ marginTop: 0, color: 'var(--primary)', fontSize: '16px' }}>📝 MISSION OBJECTIVES</h3>
            <ul style={{ fontSize: '13px', lineHeight: '1.6', paddingLeft: '20px', color: '#ccc' }}>
              <li><strong>맵 크기:</strong> 15x15 Grid</li>
              <li><strong>시간 제한:</strong> 10분</li>
              <li><strong>목표:</strong> AI보다 높은 점수를 획득하세요!</li>
              <li><span style={{ color: 'gold' }}>● Coin:</span> +5 점</li>
              <li><span style={{ color: '#00f0ff' }}>■ 영역:</span> 타일당 +1 점</li>
            </ul>
            {gameData?.winner && (
                <div style={{ marginTop: '10px', padding: '10px', border: '1px solid white', textAlign: 'center', background: gameData.winner === 'p1' ? 'var(--primary)' : 'var(--danger)', color: 'black', fontWeight: 'bold' }}>
                    RESULT: {gameData.winner.toUpperCase()} WIN!
                </div>
            )}
          </div>
        </div>
      </div>

      {/* 3. [Bottom] Full Width Error Console */}
      {/* [수정] 에러가 있을 때만 렌더링되며, 페이지 전체 스크롤을 유발함 */}
      {errorLog && (
        <div style={{ 
          minHeight: '250px', // 충분한 높이 확보
          background: '#1e1e1e', 
          borderTop: '2px solid var(--danger)', 
          display: 'flex', flexDirection: 'column',
          boxShadow: '0 -5px 20px rgba(255, 0, 0, 0.1)',
          margin: '0 20px' // 좌우 여백
        }}>
          {/* Console Header */}
          <div style={{ 
            background: 'var(--danger)', color: '#000', 
            padding: '8px 20px', fontSize: '13px', fontWeight: 'bold',
            fontFamily: 'monospace', display: 'flex', alignItems: 'center', gap: '10px'
          }}>
            <span>{errorTitle}</span>
            <span style={{ marginLeft: 'auto', fontSize: '11px', opacity: 0.8 }}>SCROLL DOWN TO SEE DETAILS</span>
          </div>
          
          {/* Console Body */}
          <div style={{ 
            flex: 1, padding: '15px 20px', 
            fontFamily: 'Consolas, Monaco, "Andale Mono", monospace', 
            fontSize: '14px', color: '#ff8b8b', whiteSpace: 'pre-wrap', lineHeight: '1.6'
          }}>
            {errorLog}
          </div>
        </div>
      )}

    </div>
  );
};

export default GameArena;