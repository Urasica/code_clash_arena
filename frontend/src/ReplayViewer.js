import React, { useRef, useEffect, useState } from 'react';

// --- Helper Functions (Drawing Logic) ---
const drawGrid = (ctx, size, width, height) => {
  ctx.strokeStyle = '#333';
  ctx.lineWidth = 1;
  const cellSize = width / size;

  for (let x = 0; x <= size; x++) {
    ctx.beginPath();
    ctx.moveTo(x * cellSize, 0);
    ctx.lineTo(x * cellSize, height);
    ctx.stroke();
  }
  for (let y = 0; y <= size; y++) {
    ctx.beginPath();
    ctx.moveTo(0, y * cellSize);
    ctx.lineTo(width, y * cellSize);
    ctx.stroke();
  }
};

const drawWalls = (ctx, walls, cellSize) => {
  ctx.fillStyle = '#444'; 
  walls.forEach(([x, y]) => {
    ctx.fillRect(x * cellSize + 1, y * cellSize + 1, cellSize - 2, cellSize - 2);
    ctx.strokeStyle = '#666';
    ctx.lineWidth = 1;
    ctx.strokeRect(x * cellSize + 1, y * cellSize + 1, cellSize - 2, cellSize - 2);
  });
};

const drawCoins = (ctx, coins, cellSize, turn) => {
  coins.forEach(([x, y]) => {
    const glow = Math.sin(Date.now() / 200) * 5 + 10;
    ctx.shadowBlur = glow;
    ctx.shadowColor = '#ffd700';
    ctx.fillStyle = '#ffd700';
    ctx.beginPath();
    ctx.arc(x * cellSize + cellSize / 2, y * cellSize + cellSize / 2, cellSize / 4, 0, Math.PI * 2);
    ctx.fill();
    ctx.shadowBlur = 0;
  });
};

const drawPlayers = (ctx, p1, p2, cellSize) => {
  if (p1.alive) {
    ctx.fillStyle = '#00ffff';
    ctx.shadowBlur = 15;
    ctx.shadowColor = '#00ffff';
    ctx.fillRect(p1.pos[0] * cellSize + 2, p1.pos[1] * cellSize + 2, cellSize - 4, cellSize - 4);
    ctx.shadowBlur = 0;
  }
  if (p2.alive) {
    ctx.fillStyle = '#ff00ff';
    ctx.shadowBlur = 15;
    ctx.shadowColor = '#ff00ff';
    ctx.fillRect(p2.pos[0] * cellSize + 2, p2.pos[1] * cellSize + 2, cellSize - 4, cellSize - 4);
    ctx.shadowBlur = 0;
  }
};

const drawTerritory = (ctx, board, cellSize) => {
  for (let y = 0; y < board.length; y++) {
    for (let x = 0; x < board[y].length; x++) {
      if (board[y][x] === 1) { 
        ctx.fillStyle = 'rgba(0, 255, 255, 0.2)';
        ctx.fillRect(x * cellSize, y * cellSize, cellSize, cellSize);
      } else if (board[y][x] === 2) { 
        ctx.fillStyle = 'rgba(255, 0, 255, 0.2)';
        ctx.fillRect(x * cellSize, y * cellSize, cellSize, cellSize);
      }
    }
  }
};

// --- Main Component ---
const ReplayViewer = ({ gameData }) => {
  const canvasRef = useRef(null);
  const [turn, setTurn] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  
  const logs = gameData?.logs || [];
  const maxTurn = logs.length > 0 ? logs.length - 1 : 0;
  const boardSize = logs[0]?.board_size || 15;

  const p1Error = gameData?.p1_error;
  const p2Error = gameData?.p2_error;
  
  // 에러 상황 판별 (로그가 0개거나 0턴뿐인데 에러가 있는 경우)
  const isCrashed = maxTurn <= 0 && (p1Error || p2Error);

  // [수정] 에러 문구 동적 결정
  let errorTitle = "SYSTEM ERROR";
  let errorDesc = "UNKNOWN ERROR OCCURRED";

  if (p1Error) {
    // 에러 메시지에 'Compilation' 텍스트가 포함되어 있으면 컴파일 에러로 간주
    if (p1Error.includes("Compilation") || p1Error.includes("SyntaxError")) {
      errorTitle = "COMPILATION FAILED";
      errorDesc = "SYNTAX ERROR DETECTED";
    } else {
      errorTitle = "RUNTIME ERROR";
      errorDesc = "CODE CRASHED DURING EXECUTION";
    }
  } else if (p2Error) {
    errorTitle = "OPPONENT CRASHED";
    errorDesc = "AI SYSTEM ERROR";
  }

  // Animation Loop
  useEffect(() => {
    let interval;
    if (isPlaying && turn < maxTurn) {
      interval = setInterval(() => {
        setTurn(prev => {
          if (prev >= maxTurn) {
            setIsPlaying(false);
            return prev;
          }
          return prev + 1;
        });
      }, 200);
    } else {
      setIsPlaying(false);
    }
    return () => clearInterval(interval);
  }, [isPlaying, turn, maxTurn]);

  // Drawing Loop
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || logs.length === 0) return;

    const ctx = canvas.getContext('2d');
    const size = 500;
    canvas.width = size;
    canvas.height = size;
    
    const cellSize = size / boardSize;
    const currentLog = logs[turn];

    // Clear Screen
    ctx.fillStyle = '#111';
    ctx.fillRect(0, 0, size, size);

    drawGrid(ctx, boardSize, size, size);
    if (currentLog) {
      drawTerritory(ctx, currentLog.board, cellSize);
      drawWalls(ctx, currentLog.walls, cellSize);
      drawCoins(ctx, currentLog.coins, cellSize, turn);
      drawPlayers(ctx, currentLog.p1, currentLog.p2, cellSize);
    }
  }, [turn, logs, boardSize]);

  return (
    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column' }}>
      
      {/* 1. 상단 상태 표시줄 */}
      <div style={{ 
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        padding: '0 10px', marginBottom: '10px',
        fontFamily: 'Orbitron', fontSize: '14px', color: 'var(--text-dim)'
      }}>
        <div>
          {isCrashed ? (
            <span style={{ color: 'var(--danger)', fontWeight: 'bold' }}>⚠ EXECUTION STOPPED</span>
          ) : (
            <span>TURN: <b style={{ color: '#fff' }}>{turn}</b> / {maxTurn}</span>
          )}
        </div>
      </div>

      {/* 2. 메인 뷰어 영역 */}
      <div style={{ 
        flex: 1, position: 'relative', 
        background: '#0d1117', border: '1px solid #30363d', borderRadius: '4px',
        display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden'
      }}>
        <canvas ref={canvasRef} style={{ width: '100%', height: 'auto', maxHeight: '100%', aspectRatio: '1/1' }} />

        {/* [수정] 크래시 오버레이 메시지 변경 */}
        {isCrashed && (
          <div style={{
            position: 'absolute', top: 0, left: 0, width: '100%', height: '100%',
            background: 'rgba(0, 0, 0, 0.85)', backdropFilter: 'blur(4px)',
            display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
            zIndex: 10
          }}>
            <div style={{ fontSize: '48px', marginBottom: '16px' }}>💥</div>
            
            <h2 style={{ 
              color: 'var(--danger)', margin: '0 0 10px 0', 
              fontFamily: 'Orbitron', letterSpacing: '2px', textAlign: 'center'
            }}>
              {errorTitle}
            </h2>
            
            <div style={{ color: '#aaa', fontSize: '14px', textAlign: 'center' }}>
              {errorDesc}
            </div>
            
            <div style={{ 
              marginTop: '20px', padding: '8px 16px', 
              border: '1px solid var(--danger)', color: 'var(--danger)', 
              fontSize: '12px', borderRadius: '20px', cursor: 'pointer'
            }}>
              CHECK ERROR LOG BELOW ⬇
            </div>
          </div>
        )}
      </div>

      {/* 3. 컨트롤러 (정상일 때만 표시) */}
      {!isCrashed && (
        <div style={{ 
          marginTop: '15px', display: 'flex', gap: '10px', justifyContent: 'center' 
        }}>
           <button onClick={() => setTurn(Math.max(0, turn - 1))} className="cyber-button icon">⏮︎</button>
           <button onClick={() => setIsPlaying(!isPlaying)} className="cyber-button icon">{isPlaying ? '❚❚' : '▶'}</button>
           <button onClick={() => setTurn(Math.min(maxTurn, turn + 1))} className="cyber-button icon">⏭︎</button>
        </div>
      )}
    </div>
  );
};

export default ReplayViewer;