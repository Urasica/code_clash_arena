import React, { useEffect, useRef, useState } from 'react';

// 내부 렌더링용 해상도 (화면 크기와 무관하게 고화질 유지)
const RENDER_CELL_SIZE = 40; 

const ReplayViewer = ({ gameData }) => {
  const canvasRef = useRef(null);
  const [turn, setTurn] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);

  useEffect(() => {
    setTurn(0);
    setIsPlaying(false);
  }, [gameData]);

  const logs = gameData?.logs || [];
  const maxTurn = logs.length;
  const canPlay = maxTurn > 1;
  const boardSize = logs.length > 0 && logs[0].board_size ? logs[0].board_size : 15;

  // 1. 그리기 로직
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    // [중요] 캔버스 내부 해상도 설정 (고정)
    canvas.width = boardSize * RENDER_CELL_SIZE;
    canvas.height = boardSize * RENDER_CELL_SIZE;

    // --- [테마 적용] 다크 모드 배경 ---
    ctx.fillStyle = '#0d1117'; // GitHub Dark Dimmed 느낌의 배경
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    // 격자 그리기 (어두운 색)
    ctx.strokeStyle = '#30363d';
    ctx.lineWidth = 1;
    for (let i = 0; i <= boardSize; i++) {
      ctx.beginPath();
      ctx.moveTo(i * RENDER_CELL_SIZE, 0);
      ctx.lineTo(i * RENDER_CELL_SIZE, boardSize * RENDER_CELL_SIZE);
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(0, i * RENDER_CELL_SIZE);
      ctx.lineTo(boardSize * RENDER_CELL_SIZE, i * RENDER_CELL_SIZE);
      ctx.stroke();
    }

    if (logs.length > 0 && turn < logs.length) {
      const currentLog = logs[turn];
      const p1 = currentLog.p1.pos;
      const p2 = currentLog.p2.pos;
      const coins = currentLog.coins || [];
      const walls = currentLog.walls || [];
      const board = currentLog.board || [];

      // 바닥(영역) 그리기 - 네온 투명도 조절
      for (let y = 0; y < boardSize; y++) {
        for (let x = 0; x < boardSize; x++) {
          const owner = board[y]?.[x]; 
          if (owner === 1) { 
            drawRect(ctx, x, y, 'rgba(0, 240, 255, 0.25)', true); // P1: Cyan Glow
          } else if (owner === 2) { 
            drawRect(ctx, x, y, 'rgba(255, 0, 170, 0.25)', true);  // P2: Pink Glow
          }
        }
      }

      // 벽 그리기 (진한 회색 + 테두리)
      walls.forEach(wall => {
        drawRect(ctx, wall[0], wall[1], '#21262d'); // Wall body
        drawBorder(ctx, wall[0], wall[1], '#484f58'); // Wall border
      });

      // 코인 그리기 (빛나는 골드)
      coins.forEach(coin => {
        // Glow effect
        ctx.shadowBlur = 10;
        ctx.shadowColor = 'gold';
        drawCircle(ctx, coin[0], coin[1], '#ffd700');
        ctx.shadowBlur = 0; // Reset
      });

      // P1 (Cyan)
      if (currentLog.p1.alive) drawRect(ctx, p1[0], p1[1], '#00f0ff');
      else drawX(ctx, p1[0], p1[1], '#00f0ff');

      // P2 (Pink)
      if (currentLog.p2.alive) drawRect(ctx, p2[0], p2[1], '#ff00aa');
      else drawX(ctx, p2[0], p2[1], '#ff00aa');
    }
  }, [turn, logs, boardSize]);

  // 재생 로직
  useEffect(() => {
    let interval;
    if (isPlaying && turn < maxTurn - 1) {
      interval = setInterval(() => {
        setTurn((prev) => prev + 1);
      }, 300);
    } else {
      setIsPlaying(false);
    }
    return () => clearInterval(interval);
  }, [isPlaying, turn, maxTurn]);

  // --- Helpers ---
  const drawRect = (ctx, x, y, color, isFill = false) => {
    ctx.fillStyle = color;
    // 꽉 채우거나 약간 여백을 줌
    const gap = isFill ? 0 : 2;
    ctx.fillRect(x * RENDER_CELL_SIZE + gap, y * RENDER_CELL_SIZE + gap, RENDER_CELL_SIZE - (gap*2), RENDER_CELL_SIZE - (gap*2));
  };

  const drawBorder = (ctx, x, y, color) => {
    ctx.strokeStyle = color;
    ctx.lineWidth = 2;
    ctx.strokeRect(x * RENDER_CELL_SIZE + 2, y * RENDER_CELL_SIZE + 2, RENDER_CELL_SIZE - 4, RENDER_CELL_SIZE - 4);
  };
  
  const drawCircle = (ctx, x, y, color) => {
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(x * RENDER_CELL_SIZE + RENDER_CELL_SIZE / 2, y * RENDER_CELL_SIZE + RENDER_CELL_SIZE / 2, RENDER_CELL_SIZE / 3.5, 0, 2 * Math.PI);
    ctx.fill();
  };

  const drawX = (ctx, x, y, color) => {
    ctx.strokeStyle = color;
    ctx.lineWidth = 3;
    ctx.beginPath();
    ctx.moveTo(x * RENDER_CELL_SIZE + 8, y * RENDER_CELL_SIZE + 8);
    ctx.lineTo((x + 1) * RENDER_CELL_SIZE - 8, (y + 1) * RENDER_CELL_SIZE - 8);
    ctx.moveTo((x + 1) * RENDER_CELL_SIZE - 8, y * RENDER_CELL_SIZE + 8);
    ctx.lineTo(x * RENDER_CELL_SIZE + 8, (y + 1) * RENDER_CELL_SIZE - 8);
    ctx.stroke();
  };

  return (
    <div style={{ textAlign: 'center', width: '100%', height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{ marginBottom: '10px', fontSize: '18px', fontWeight: 'bold', fontFamily: 'Orbitron', color: 'var(--primary)' }}>
        TURN: <span style={{color: '#fff'}}>{turn}</span> / {maxTurn - 1 > 0 ? maxTurn - 1 : 0}
      </div>
      
      {/* [핵심 수정] 
         부모 박스 크기에 맞춰서 100%로 렌더링하되, 
         aspect-ratio를 1/1로 유지하여 찌그러지지 않게 함 
      */}
      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 0 }}>
        <canvas 
          ref={canvasRef} 
          style={{ 
            width: '100%', 
            height: 'auto', 
            maxWidth: '100%', 
            maxHeight: '100%',
            aspectRatio: '1/1', // 정사각형 비율 유지
            border: '1px solid #30363d',
            boxShadow: '0 0 20px rgba(0,0,0,0.5)',
            imageRendering: 'pixelated' // 픽셀 아트처럼 선명하게
          }}
        />
      </div>
      
      <div style={{ marginTop: '15px', display: 'flex', justifyContent: 'center', gap: '5px' }}>
        <button className="cyber-button" style={{padding: '5px 10px', fontSize: '12px'}} onClick={() => setTurn(0)} disabled={!canPlay}>⏮</button>
        <button className="cyber-button" style={{padding: '5px 10px', fontSize: '12px'}} onClick={() => setTurn(Math.max(0, turn - 1))} disabled={turn === 0}>◀</button>
        
        <button 
          className="cyber-button" 
          onClick={() => setIsPlaying(!isPlaying)} 
          disabled={!canPlay || (turn >= maxTurn - 1 && !isPlaying)}
          style={{ width: '60px', fontWeight: 'bold', color: isPlaying ? 'var(--secondary)' : 'var(--primary)', borderColor: isPlaying ? 'var(--secondary)' : 'var(--primary)' }}
        >
          {isPlaying ? '⏸' : '▶'}
        </button>
        
        <button className="cyber-button" style={{padding: '5px 10px', fontSize: '12px'}} onClick={() => setTurn(Math.min(maxTurn - 1, turn + 1))} disabled={turn >= maxTurn - 1}>▶</button>
        <button className="cyber-button" style={{padding: '5px 10px', fontSize: '12px'}} onClick={() => setTurn(maxTurn - 1)} disabled={!canPlay}>⏭</button>
      </div>
    </div>
  );
};

export default ReplayViewer;