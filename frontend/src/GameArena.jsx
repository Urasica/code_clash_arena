import React, { useState, useEffect, useRef } from 'react';
import Editor from "@monaco-editor/react"; 
import ReplayViewer from './ReplayViewer';
import { TEMPLATES } from './CodeTemplates';
import {
  compileLandGrabCode,
  runLandGrabMatch,
  startLandGrabMatch,
} from './features/landGrab/landGrabApi';
import { getMatchOutcome } from './features/landGrab/matchOutcome';
import { createStompClient } from './shared/realtime/createStompClient';

const GameArena = ({ onBack, difficulty, matchData }) => {
  const [matchId, setMatchId] = useState(null);
  const [gameData, setGameData] = useState(null);
  const [status, setStatus] = useState('init'); 
  const [loading, setLoading] = useState(false);
  
  const [mode, setMode] = useState('AI'); 
  const [language, setLanguage] = useState('python');
  const [userCode, setUserCode] = useState(TEMPLATES['python']);
  const [timeLeft, setTimeLeft] = useState(600); 

  const [isWaitingOpponent, setIsWaitingOpponent] = useState(false);
  const [opponentSubmitted, setOpponentSubmitted] = useState(false); 
  const [myRole, setMyRole] = useState(null); // [추가] 내 역할

  const stompClient = useRef(null);
  const handleRunMatchRef = useRef(null);

  // 1. 초기화 & 모드 설정
  useEffect(() => {
    if (matchData) {
      console.log("⚔️ PvP Match Data Received:", matchData);
      setMode('PvP');
      setMatchId(matchData.matchId);
      setMyRole(matchData.myRole);
      
      // [수정] 맵 데이터 파싱 (평탄화된 구조 대응)
      // matchData.mapData 자체가 {walls:[], coins:[]} 형태일 가능성이 높음
      // 혹시 모르니 mapData.map 체크도 유지하되 우선순위 조정
      let mapSource = matchData.mapData;
      if (mapSource && mapSource.map) {
          mapSource = mapSource.map;
      }
      
      initializeGameBoard(mapSource);
      connectPvPSocket(matchData.matchId, matchData.myRole);
      setStatus('ready');
    } else {
      setMode('AI');
      setStatus('init');
    }

    return () => {
      if (stompClient.current) {
          console.log("🛑 Deactivating WebSocket...");
          stompClient.current.deactivate(); // 연결 끊기
          stompClient.current = null;       // 참조 제거
      }
    };
  }, [matchData]);

  // 2. 타이머 로직
  useEffect(() => {
    let timer;
    if (status === 'ready' && timeLeft > 0) { 
      timer = setInterval(() => {
        setTimeLeft((prev) => prev - 1);
      }, 1000);
    } else if (timeLeft === 0 && status === 'ready') {
      handleRunMatchRef.current?.();
      alert("시간초과! 코드가 자동으로 제출되었습니다.");
    }
    return () => clearInterval(timer);
  }, [status, timeLeft]);

  const formatTime = (seconds) => {
    const m = Math.floor(seconds / 60).toString().padStart(2, '0');
    const s = (seconds % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  };

  // 3. 초기 보드 데이터 생성
  const initializeGameBoard = (map) => {
    const boardSize = 15;
    const walls = map?.walls || [];
    const coins = map?.coins || [];
    
    console.log(`🗺️ Map Init - Walls: ${walls.length}, Coins: ${coins.length}`);

    const initialBoard = Array(boardSize).fill(0).map(() => Array(boardSize).fill(0));
    initialBoard[0][0] = 1; 
    initialBoard[boardSize-1][boardSize-1] = 2;

    setGameData({
      logs: [{
        turn: 0, 
        board_size: boardSize, 
        walls: walls, 
        coins: coins,
      p1: { pos: [0, 0], alive: true },
      p2: { pos: [boardSize-1, boardSize-1], alive: true },
        board: initialBoard
      }]
    });
  };

  // 4. WebSocket 연결
  const connectPvPSocket = (id, role) => {
    const client = createStompClient({
      onConnect: () => {
        console.log("✅ PvP Socket Connected");
        client.publish({
            destination: '/app/game/join',
            body: JSON.stringify({ matchId: id })
        });

        client.subscribe(`/topic/game/${id}`, (message) => {
          const res = JSON.parse(message.body);
          console.log("📩 Message Received:", res);

          if (res.type === 'NOTIFICATION' && res.message === 'PLAYER_SUBMITTED') {
             // 메시지에 담긴 role이 내 role과 다를 때만 '상대 제출'로 표시
             if (res.role !== role) {
                 setOpponentSubmitted(true);
             }
             return;
          }

          if (res.type === 'RESULT' || res.type === 'ERROR') {
            setIsWaitingOpponent(false);
            setLoading(false);
            
            // "OPPONENT_DISCONNECTED"도 여기서 처리됨 -> renderResultOverlay가 "VICTORY" 표시함
            setGameData(res); 
            setStatus('finished');
          }
        });
      }
    });
    client.activate();
    stompClient.current = client;
  };

  // 5. [AI] 매치 시작
  const handleStartMatch = async () => {
    setLoading(true);
    try {
      const res = await startLandGrabMatch();
      // 백엔드 구조 변경에 따라 res.data 자체가 map 정보를 포함 (matchId, walls, coins...)
      // 하지만 AI 모드는 여전히 {matchId, map: {...}} 구조일 수 있으므로 확인 필요
      // LandGrabService 수정으로 {matchId, walls, coins} 형태로 옴
      const { matchId, ...map } = res.data; 
      
      setMatchId(matchId);
      initializeGameBoard(map); // map 객체 전달
      
      setStatus('ready');
      setTimeLeft(600);
    } catch (err) {
      if (err.response && err.response.status === 401) {
        alert("세션이 만료되었습니다. 다시 로그인해주세요.");
        onBack();
      } else {
        alert("Error: " + (err.response?.data?.error || err.message));
      }
    }
    setLoading(false);
  };

  // 6. 코드 실행/제출
  const handleRunMatch = async () => {
    if (!matchId) return;
    
    if (mode === 'PvP') {
        if (stompClient.current && stompClient.current.connected) {
            setLoading(true); 
            setIsWaitingOpponent(true); 
            setStatus('submitted');

            stompClient.current.publish({
                destination: '/app/game/submit',
                body: JSON.stringify({
                    matchId: matchId,
                    code: userCode,
                    language: language
                })
            });
        } else {
            alert("서버 연결이 끊겼습니다.");
        }
        return;
    }

    // [AI 제출 로직]
    setGameData(null); 
    try {
      setLoading('COMPILING...');
      const compileRes = await compileLandGrabCode({
        matchId: matchId,
        userCode: userCode,
        language: language
      });

      if (compileRes.data.status === 'error') {
        setGameData({ p1_error: compileRes.data.error });
        setStatus('finished');
        setLoading(false);
        return;
      }

      setLoading('BATTLE...');
      const runRes = await runLandGrabMatch({
        matchId: matchId,
        userCode: userCode,
        language: language,
        difficulty: difficulty
      });
      
      setGameData(runRes.data);
      setStatus('finished');

    } catch (err) {
       // 에러 핸들링
       if (err.response && err.response.status === 401) {
         alert("세션 만료.");
         onBack();
       } else {
         alert("Error: " + err.message);
       }
    }
    setLoading(false);
  };

  handleRunMatchRef.current = handleRunMatch;

  const renderResultOverlay = () => {
      // 게임이 안 끝났거나 데이터가 없으면 표시 안 함
      if (status !== 'finished' || !gameData) return null;

      const playerRole = mode === 'AI' ? 'p1' : myRole;
      const { title, color, reason } = getMatchOutcome(gameData, playerRole);

      return (
          <div style={{
              position: 'absolute', top: 0, left: 0, width: '100%', height: '100%',
              background: 'rgba(0,0,0,0.85)', backdropFilter: 'blur(5px)', // 배경 흐리게
              display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
              zIndex: 100, // 최상단 노출
              animation: 'fadeIn 0.5s'
          }}>
              {/* 타이틀 (VICTORY / DEFEAT) */}
              <h1 style={{ 
                  fontSize: '80px', color: color, margin: 0, 
                  fontFamily: 'Orbitron', textShadow: `0 0 30px ${color}`, letterSpacing: '5px'
              }}>
                  {title}
              </h1>
              
              {/* 서브 텍스트 (사유) */}
              <h3 style={{ color: '#ccc', marginTop: '10px', fontSize: '20px', fontFamily: 'Orbitron' }}>
                  {reason}
              </h3>
              
              {/* 런타임 에러 상세 로그 (있을 경우만 표시) */}
              {(gameData.p1_error || gameData.p2_error) && (
                  <div style={{ 
                      background: 'rgba(50, 0, 0, 0.8)', border: '1px solid red', 
                      padding: '20px', marginTop: '30px', maxWidth: '600px', borderRadius: '4px' 
                  }}>
                      <div style={{color: 'red', fontWeight: 'bold', marginBottom: '10px'}}>🛑 ERROR LOG:</div>
                      <div style={{color: '#ffcccc', fontSize: '13px', whiteSpace: 'pre-wrap', fontFamily: 'monospace'}}>
                          {gameData.p1_error || gameData.p2_error}
                      </div>
                  </div>
              )}

              {/* 버튼 그룹 */}
              <div style={{ marginTop: '50px', display: 'flex', gap: '20px' }}>
                  <button className="cyber-button" onClick={onBack} style={{ padding: '15px 40px', fontSize: '18px' }}>
                      BACK TO LOBBY
                  </button>
                  
                  {/* 로그가 존재하면 리플레이 버튼 표시 (단, 탈주 등으로 로그가 없으면 안 뜸) */}
                  {gameData.logs && gameData.logs.length > 0 && (
                      <button 
                          className="cyber-button secondary" 
                          onClick={() => setStatus('replay')} // 상태를 replay로 바꿔서 오버레이를 끔
                          style={{ padding: '15px 40px', fontSize: '18px' }}
                      >
                          WATCH CODE
                      </button>
                  )}
              </div>
          </div>
      );
  };

  // 에러 로그 추출
  const errorLog = gameData?.p1_error || gameData?.p2_error;
  const errorTitle = gameData?.p1_error 
    ? "❌ COMPILATION / RUNTIME ERROR (PLAYER 1)" 
    : "❌ SYSTEM ERROR (PLAYER 2)";

  // 역할에 따른 색상
  const getRoleColor = () => myRole === 'p1' ? '#00f0ff' : (myRole === 'p2' ? '#ff0055' : '#888');
  const getOpponentColor = () => myRole === 'p1' ? '#ff0055' : (myRole === 'p2' ? '#00f0ff' : '#888');

  return (
    <div className="cyber-container" style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', paddingBottom: '20px', position: 'relative' }}>
      
      {/* Header */}
      <header className="glass-panel" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', padding: '10px 20px', flexShrink: 0, position: 'relative' }}>
        <div style={{ display: 'flex', gap: '20px', flex: 1 }}>
          <button className="cyber-button secondary" style={{ fontSize: '12px', padding: '5px 10px' }} onClick={onBack}>◀ EXIT</button>
          <h2 style={{ margin: 0, color: 'var(--primary)', fontFamily: 'Orbitron' }}>
            ARENA 01 <span style={{ fontSize: '14px', color: '#888' }}>
                [{mode === 'PvP' ? 'PVP MATCH' : difficulty.toUpperCase()}]
            </span>
          </h2>
        </div>

        {/* [수정] PvP 상태 메시지 바: 게임 종료 상태(finished) 체크 추가 */}
        {mode === 'PvP' && (
            <div style={{ position: 'absolute', left: '50%', transform: 'translateX(-50%)', display: 'flex', gap: '30px', alignItems: 'center' }}>
                
                {/* 나의 상태 */}
                <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: '12px', color: '#888' }}>YOU ({myRole ? myRole.toUpperCase() : '?'})</div>
                    <div style={{ 
                        // 종료되면 회색(#888), 대기중이면 초록, 아니면 내 팀 색상
                        color: status === 'finished' ? '#888' : (isWaitingOpponent ? 'var(--success)' : getRoleColor()), 
                        fontWeight: 'bold' 
                    }}>
                        {status === 'finished' ? 'FINISHED' : (isWaitingOpponent ? 'READY' : 'CODING')}
                    </div>
                </div>
                
                <div style={{ fontSize: '20px', color: '#444', fontFamily: 'Orbitron' }}>VS</div>

                {/* 상대방 상태 */}
                <div style={{ textAlign: 'left' }}>
                    <div style={{ fontSize: '12px', color: '#888' }}>OPPONENT</div>
                    <div style={{ 
                        color: status === 'finished' ? '#888' : (opponentSubmitted ? 'var(--success)' : getOpponentColor()), 
                        fontWeight: 'bold' 
                    }}>
                        {status === 'finished' ? 'FINISHED' : (opponentSubmitted ? 'READY' : 'CODING')}
                    </div>
                </div>
            </div>
         )}

        <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
          {(status === 'ready' || isWaitingOpponent) && (
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
             {mode === 'AI' && status === 'init' && (
                <button className="cyber-button" onClick={handleStartMatch} disabled={loading}>GENERATE MAP</button>
              )}
              {status === 'ready' && (
                 <button 
                    className="cyber-button" 
                    onClick={handleRunMatch} 
                    disabled={loading || isWaitingOpponent}
                    style={{ background: isWaitingOpponent ? '#333' : undefined }}
                 >
                   {isWaitingOpponent ? '⏳ WAITING...' : (loading === true ? 'PROCESSING...' : (loading || '🚀 SUBMIT CODE'))} 
                </button>
              )}
              {status === 'finished' && (
                 <button 
                    className="cyber-button" 
                    onClick={mode === 'PvP' ? onBack : handleStartMatch}
                    style={{ 
                        borderColor: mode === 'PvP' ? 'var(--secondary)' : undefined,
                        color: mode === 'PvP' ? 'var(--secondary)' : undefined
                    }}
                 >
                    {mode === 'PvP' ? '◀ BACK TO LOBBY' : '🔄 RETRY'}
                 </button>
              )}
          </div>
        </div>
      </header>

      {/* Main Content */}
      <div style={{ display: 'flex', gap: '20px', height: '80vh', marginBottom: errorLog ? '20px' : '0' }}>
        <div className="glass-panel" style={{ flex: 1, display: 'flex', flexDirection: 'column', padding: '0', overflow: 'hidden', minWidth: 0, borderColor: mode==='PvP' ? getRoleColor() : undefined }}>
          <div style={{ padding: '10px', borderBottom: '1px solid var(--border-color)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#0f1219' }}>
            <select 
              className="cyber-input" 
              style={{ width: '150px', padding: '5px' }} 
              value={language} 
              onChange={(e) => {
                setLanguage(e.target.value);
                setUserCode(TEMPLATES[e.target.value] || "");
              }}
              disabled={isWaitingOpponent} 
            >
              <option value="python">Python 3.8</option>
              <option value="java">Java 17</option>
              <option value="cpp">C++ 17</option>
              <option value="c">C 11</option>
              <option value="javascript">Node.js</option>
            </select>
            <div style={{ fontSize: '12px', color: 'var(--text-dim)' }}>VS Code Style Editor</div>
          </div>

          {status === 'finished' && renderResultOverlay()}

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
                 automaticLayout: true,
                 readOnly: status !== 'ready' || isWaitingOpponent, 
               }}
             />
             {isWaitingOpponent && (
                <div style={{
                    position: 'absolute', top: 0, left: 0, width: '100%', height: '100%',
                    background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(3px)',
                    display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
                    zIndex: 10
                }}>
                    <div className="spinner"></div>
                    <h3 style={{ marginTop: '20px', color: 'var(--primary)' }}>CODE SUBMITTED</h3>
                    <p style={{ color: '#ccc' }}>
                        {opponentSubmitted 
                            ? "Both players ready! Processing match..." 
                            : "Waiting for opponent to submit..."}
                    </p>
                </div>
             )}
          </div>
        </div>

        <div style={{ width: '500px', display: 'flex', flexDirection: 'column', gap: '20px', minWidth: 0 }}>
          <div className="glass-panel" style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', background: '#000', padding: '20px', minHeight: 0 }}>
            {gameData ? (
              <ReplayViewer gameData={gameData} />
            ) : (
              <div style={{ color: '#555', fontFamily: 'Orbitron', textAlign: 'center' }}>
                <div style={{fontSize: '40px', marginBottom: '10px'}}>
                    {isWaitingOpponent ? '⚔️' : '📡'}
                </div>
                {isWaitingOpponent 
                    ? '전투 준비 중... 상대방을 기다리는 중입니다.' 
                    : (mode === 'PvP' ? '상대와 연결되었습니다. 코드를 작성하세요!' : '맵 생성을 누르면 시작합니다...')}
              </div>
            )}
          </div>

          <div className="glass-panel" style={{ height: '200px', minHeight: '200px', overflowY: 'auto' }}>
            <h3 style={{ marginTop: 0, color: 'var(--primary)', fontSize: '16px' }}>📝 MISSION OBJECTIVES</h3>
            <ul style={{ fontSize: '13px', lineHeight: '1.6', paddingLeft: '20px', color: '#ccc' }}>
              <li><strong>맵 크기:</strong> 15x15 Grid</li>
              <li><strong>시간 제한:</strong> 10분</li>
              <li><strong>목표:</strong> {mode === 'PvP' ? '상대 플레이어' : 'AI'}보다 높은 점수를 획득하세요!</li>
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

      {errorLog && (
        <div style={{ minHeight: '250px', background: '#1e1e1e', borderTop: '2px solid var(--danger)', display: 'flex', flexDirection: 'column', margin: '0 20px', boxShadow: '0 -5px 20px rgba(255, 0, 0, 0.1)' }}>
          <div style={{ background: 'var(--danger)', color: '#000', padding: '8px 20px', fontSize: '13px', fontWeight: 'bold', fontFamily: 'monospace', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span>{errorTitle}</span>
            <span style={{ marginLeft: 'auto', fontSize: '11px', opacity: 0.8 }}>SCROLL DOWN TO SEE DETAILS</span>
          </div>
          <div style={{ flex: 1, padding: '15px 20px', fontFamily: 'Consolas, monospace', fontSize: '14px', color: '#ff8b8b', whiteSpace: 'pre-wrap', lineHeight: '1.6' }}>
            {errorLog}
          </div>
        </div>
      )}

    </div>
  );
};

export default GameArena;
