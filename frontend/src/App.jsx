import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { 
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, AreaChart, Area
} from 'recharts';
import { 
  TrendingUp, Play, Square, Trash2, Plus, RefreshCw, Edit, ChevronLeft, ChevronRight,
  Activity, Wallet, Briefcase, ListFilter, ShieldAlert, Eye, Search, X, Check, Cpu, Bell, PenTool, Send
} from 'lucide-react';

const API_BASE = 'http://localhost:8081/api/dashboard';

const COLORS = ['#6366f1', '#10b981', '#ef4444', '#f59e0b', '#ec4899', '#8b5cf6', '#3b82f6'];

export default function App() {
  const [activeTab, setActiveTab] = useState('dashboard');
  const [summary, setSummary] = useState(null);
  const [orders, setOrders] = useState([]);
  const [bots, setBots] = useState([]);
  const [prices, setPrices] = useState([]);
  const [diaries, setDiaries] = useState([]); // AI 오답노트 전체 데이터
  
  // AI 일기장 캐러셀 제어 인덱스
  const [diaryIndex, setDiaryIndex] = useState(0);
  
  // 실시간 핫 마켓 종목 정보
  const [hotStocks, setHotStocks] = useState({ gainers: [], losers: [], volumes: [] });
  
  // 종목 검색 상태 (봇 관리 탭용)
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);

  // 종목 검색 상태 (대시보드 마켓 트렌드 탭용)
  const [dashSearchQuery, setDashSearchQuery] = useState('');
  const [dashSearchResults, setDashSearchResults] = useState([]);

  // 수동 매매 모달 내 종목 검색 상태
  const [manualSearchQuery, setManualSearchQuery] = useState('');
  const [manualSearchResults, setManualSearchResults] = useState([]);
  
  // 차트 뷰 제어 상태
  const [chartType, setChartType] = useState('asset'); // 'asset' 혹은 'stock'
  const [selectedStock, setSelectedStock] = useState({ symbol: '005930', name: '삼성전자' });
  const [stockCandles, setStockCandles] = useState([]);

  // Gemini AI 리포트 분석 상태
  const [aiReport, setAiReport] = useState(null);
  const [aiLoading, setAiLoading] = useState(false);

  // AI 투자위원회 (Gemini + ChatGPT) 및 감성 여론 상태
  const [aiCommitteeReport, setAiCommitteeReport] = useState(null);
  const [committeeLoading, setCommitteeLoading] = useState(false);
  const [sentimentGauge, setSentimentGauge] = useState(null);
  const [arenaRanking, setArenaRanking] = useState([]);
  const [aiTab, setAiTab] = useState('consensus'); // 'consensus', 'gemini', 'openai', 'sentiment'

  // AI Q&A 대화형 챗봇 상태
  const [chatMessages, setChatMessages] = useState([
    {
      sender: 'ai',
      text: '반갑습니다, 주주님! 챗봇 주식 컨설턴트 AI입니다. 🤖\n국내 실시간 시세 및 퀀트 데이터를 바탕으로 상승 확률이 높은 종목 추천이나 매수 타점, 시장 동향에 관해 무엇이든 물어보세요!',
      recommendedStocks: []
    }
  ]);
  const [chatInput, setChatInput] = useState('');
  const [chatLoading, setChatLoading] = useState(false);

  // 봇 추가/수정 폼 상태
  const [newBot, setNewBot] = useState({
    stockCode: '',
    stockName: '',
    algorithmType: 'ACCUMULATION',
    active: false
  });

  // 수정 중인 봇 정보 상태 (null 이면 신규 추가 모드)
  const [editingBot, setEditingBot] = useState(null);

  // 매수 약정 기준 단위 ('amount': 금액 기준(원), 'quantity': 주수 기준(주))
  const [tradeUnitType, setTradeUnitType] = useState('amount');

  // 알고리즘별 개별 수치 입력 필드 상태
  const [algoParams, setAlgoParams] = useState({
    amount: 10000,              // 1회 약정 금액
    quantity: 1,                // 1회 약정 수량 (주)
    intervalMinutes: 60,        // 적립식: 매수 주기 (분) - 기본 1시간으로 변경
    dropPercent: 5.0,           // 낙폭과대: 최고가 대비 하락률 (%)
    lookbackDays: 10,           // 낙폭과대: 최고가 감시 범위 (일)
    targetProfitPercent: 10.0,  // 낙폭과대: 목표 수익률 (%)
    shortPeriod: 5,             // 이평선: 단기 이평선 (일)
    longPeriod: 20              // 이평선: 장기 이평선 (일)
  });

  // 위험 관리 (손절/익절) 설정 상태
  const [riskParams, setRiskParams] = useState({
    useRiskControl: false,
    stopLossPercent: 3.0,
    takeProfitPercent: 10.0
  });

  // 포트폴리오 리밸런싱 타겟 비중 상태
  const [rebalanceTargets, setRebalanceTargets] = useState([
    { code: '005930', name: '삼성전자', weight: 50 },
    { code: '000660', name: 'SK하이닉스', weight: 50 }
  ]);

  // 백테스팅 결과 상태
  const [backtesting, setBacktesting] = useState(false);
  const [backtestResult, setBacktestResult] = useState(null);

  // 실시간 체결 토스트 알림 상태
  const [activeToast, setActiveToast] = useState(null);
  const [prevOrdersCount, setPrevOrdersCount] = useState(0);

  // 즉시 수동 매매 팝업 모달 상태
  const [showManualModal, setShowManualModal] = useState(false);
  const [manualOrder, setManualOrder] = useState({
    stockCode: '005930',
    stockName: '삼성전자',
    orderType: 'BUY',
    quantity: 1
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // 데이터 새로고침
  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [resSummary, resOrders, resBots, resPrices, resHot, resDiaries] = await Promise.all([
        axios.get(`${API_BASE}/summary`),
        axios.get(`${API_BASE}/orders`),
        axios.get(`${API_BASE}/bots`),
        axios.get(`${API_BASE}/prices`),
        axios.get(`${API_BASE}/hot-stocks`),
        axios.get(`${API_BASE}/diaries`)
      ]);
      setSummary(resSummary.data);
      setOrders(resOrders.data);
      setBots(resBots.data);
      setPrices(resPrices.data);
      setHotStocks(resHot.data);
      
      const newDiaries = resDiaries.data;
      setDiaries(newDiaries);
    } catch (err) {
      console.error(err);
      setError('백엔드 서버와 연결할 수 없습니다. run.bat 실행 상태를 확인하세요.');
    } finally {
      setLoading(false);
    }
  };

  // diaries 로드 완료 시 캐러셀 인덱스 초기값 세팅 (최신 일기가 기본 활성화)
  useEffect(() => {
    if (diaries.length > 0) {
      setDiaryIndex(diaries.length - 1);
    }
  }, [diaries]);

  // 실시간 주문 체결(FILLED) 신규 유입 감시 및 토스트 팝업 격발
  useEffect(() => {
    if (orders.length > 0) {
      if (prevOrdersCount > 0 && orders.length > prevOrdersCount) {
        const latestOrder = orders[0];
        if (latestOrder.status === 'FILLED') {
          const actionStr = latestOrder.orderType === 'BUY' ? '매수 완료' : '매도 완료';
          const qtyStr = latestOrder.quantity ? `${latestOrder.quantity}주` : '수량';
          const message = `🔔 [자동 매매 체결] ${latestOrder.stockName} ${qtyStr} ${actionStr}! (체결가: ₩${latestOrder.price.toLocaleString()}원)`;
          
          setActiveToast({ message, type: latestOrder.orderType });

          const timer = setTimeout(() => {
            setActiveToast(null);
          }, 5000);
        }
      }
      setPrevOrdersCount(orders.length);
    }
  }, [orders]);

  // Gemini AI 퀀트 리포트 백그라운드 호출
  const fetchAiAnalysis = async (symbol) => {
    setAiLoading(true);
    setAiReport(null);
    try {
      const res = await axios.get(`${API_BASE}/ai-analysis`, {
        params: { symbol }
      });
      setAiReport(res.data);
    } catch (err) {
      console.error('Gemini AI analysis failed', err);
    } finally {
      setAiLoading(false);
    }
  };

  // AI 투자위원회 (Gemini + ChatGPT) 분석 호출
  const fetchAiCommitteeReport = async (symbol) => {
    setCommitteeLoading(true);
    setAiCommitteeReport(null);
    try {
      const res = await axios.get(`${API_BASE}/ai-committee`, {
        params: { symbol }
      });
      setAiCommitteeReport(res.data);
    } catch (err) {
      console.error('AI Committee analysis failed', err);
    } finally {
      setCommitteeLoading(false);
    }
  };

  // 여론 온도계 분석 호출
  const fetchSentimentGauge = async (symbol) => {
    try {
      const res = await axios.get(`${API_BASE}/sentiment`, {
        params: { symbol }
      });
      setSentimentGauge(res.data);
    } catch (err) {
      console.error('Sentiment gauge fetch failed', err);
    }
  };

  // 전략 배틀 아레나 랭킹 호출
  const fetchArenaRanking = async () => {
    try {
      const res = await axios.get(`${API_BASE}/arena`);
      setArenaRanking(res.data);
    } catch (err) {
      console.error('Arena ranking failed', err);
    }
  };

  // 특정 종목의 캔들 조회 (실시간 주가 차트용)
  const fetchStockCandles = async (symbol, name) => {
    try {
      const res = await axios.get(`${API_BASE}/candles`, {
        params: { symbol, count: 15 }
      });
      const reversed = [...res.data].reverse();
      setStockCandles(reversed);
      setSelectedStock({ symbol, name });
      setChartType('stock');
      
      // 듀얼 AI 투자위원회 및 여론 온도계 동시 호출
      fetchAiAnalysis(symbol);
      fetchAiCommitteeReport(symbol);
      fetchSentimentGauge(symbol);
    } catch (err) {
      console.error('Failed to fetch stock candles', err);
    }
  };

  useEffect(() => {
    fetchData();
    fetchArenaRanking();
    const timer = setInterval(() => {
      fetchData();
      fetchArenaRanking();
    }, 15000);
    return () => clearInterval(timer);
  }, []);

  // 초기 관심 종목 데이터 수신 시 첫 종목의 차트 미리 채워두기
  useEffect(() => {
    if (prices.length > 0 && stockCandles.length === 0) {
      const first = prices[0];
      const botObj = bots.find(b => b.stockCode === first.symbol);
      fetchStockCandles(first.symbol, botObj ? botObj.stockName : '관심 종목');
    }
  }, [prices]);

  // 봇 관리용 검색 핸들러
  const handleSearchChange = async (e) => {
    const val = e.target.value;
    setSearchQuery(val);
    if (val.trim().length > 0) {
      try {
        const res = await axios.get(`${API_BASE}/search-stocks`, { params: { query: val } });
        setSearchResults(res.data);
      } catch (err) {
        console.error('Search query failed', err);
      }
    } else {
      setSearchResults([]);
    }
  };

  // 대시보드 핫 마켓용 검색 핸들러
  const handleDashSearchChange = async (e) => {
    const val = e.target.value;
    setDashSearchQuery(val);
    if (val.trim().length > 0) {
      try {
        const res = await axios.get(`${API_BASE}/search-stocks`, { params: { query: val } });
        setDashSearchResults(res.data);
      } catch (err) {
        console.error('Dashboard search failed', err);
      }
    } else {
      setDashSearchResults([]);
    }
  };

  // 수동 매매 모달 내 검색 핸들러
  const handleManualSearchChange = async (e) => {
    const val = e.target.value;
    setManualSearchQuery(val);
    if (val.trim().length > 0) {
      try {
        const res = await axios.get(`${API_BASE}/search-stocks`, { params: { query: val } });
        setManualSearchResults(res.data);
      } catch (err) {
        console.error('Manual search failed', err);
      }
    } else {
      setManualSearchResults([]);
    }
  };

  const selectSearchItem = (item) => {
    setNewBot({
      ...newBot,
      stockCode: item.symbol || item.code,
      stockName: item.name
    });
    setSearchQuery('');
    setSearchResults([]);
  };

  const selectManualSearchItem = (item) => {
    setManualOrder({
      ...manualOrder,
      stockCode: item.symbol || item.code,
      stockName: item.name
    });
    setManualSearchQuery('');
    setManualSearchResults([]);
  };

  // 수동 매매 주문 실행 요청
  const handleManualTradeSubmit = async (e) => {
    e.preventDefault();
    try {
      const res = await axios.post(`${API_BASE}/orders/manual`, manualOrder);
      if (res.data) {
        setShowManualModal(false);
        fetchData();
      } else {
        alert('보유 중인 가상 주식이 없거나 예수금이 부족하여 주문 체결에 실패했습니다.');
      }
    } catch (err) {
      console.error(err);
      alert('주문 처리 중 오류가 발생했습니다.');
    }
  };

  // 💬 AI Q&A 메시지 전송 처리
  const handleSendChatMessage = async (overrideMsg) => {
    const query = overrideMsg || chatInput;
    if (!query || !query.trim() || chatLoading) return;

    const userMsgObj = { sender: 'user', text: query };
    setChatMessages(prev => [...prev, userMsgObj]);
    if (!overrideMsg) setChatInput('');
    setChatLoading(true);

    try {
      const res = await axios.post(`${API_BASE}/ai-chat`, { message: query });
      const aiMsgObj = {
        sender: 'ai',
        text: res.data.answerText || '답변을 생성하지 못했습니다.',
        recommendedStocks: res.data.recommendedStocks || []
      };
      setChatMessages(prev => [...prev, aiMsgObj]);
    } catch (err) {
      console.error('AI chat failed', err);
      setChatMessages(prev => [...prev, {
        sender: 'ai',
        text: '죄송합니다, 주주님. 질의 처리 중 오류가 발생했습니다.',
        recommendedStocks: []
      }]);
    } finally {
      setChatLoading(false);
    }
  };

  // 돋보기 🔍 클릭 시 AI 추천 종목 차트를 메인 대시보드에 즉시 띄우기
  const handleViewStockFromChat = (symbol, name) => {
    fetchStockCandles(symbol, name);
    setActiveTab('dashboard');
    window.scrollTo({ top: 100, behavior: 'smooth' });
  };

  // ➕ (자동매매 등록) 클릭 시: 종목 정보 기입 및 AI 권장 파라미터 세팅 (Pre-fill)
  const handleQuickBotRegister = (symbol, name) => {
    const defaultBot = {
      stockCode: symbol,
      stockName: name,
      algorithmType: 'ACCUMULATION',
      active: false
    };

    if (aiReport) {
      // AI 추천 수량이 존재할 때 주수단위 거래 모드로 자동 전환 및 pre-fill 기입
      setTradeUnitType('quantity');

      const rp = aiReport.recommendedParameters || {};
      setAlgoParams({
        amount: aiReport.recommendedAmount || rp.amount || 50000,
        quantity: aiReport.recommendedQuantity || 1, // AI가 예수금 대비 최적화해서 제안한 주수 연동
        intervalMinutes: rp.intervalMinutes || 60, // 기본 60분
        dropPercent: rp.dropPercent || 5.0,
        lookbackDays: 10,
        targetProfitPercent: rp.takeProfitPercent || 10.0,
        shortPeriod: 5,
        longPeriod: 20
      });

      setRiskParams({
        useRiskControl: (rp.stopLossPercent || rp.takeProfitPercent) ? true : false,
        stopLossPercent: rp.stopLossPercent || 3.0,
        takeProfitPercent: rp.takeProfitPercent || 10.0
      });
    } else {
      setTradeUnitType('amount');
      setAlgoParams(prev => ({
        ...prev,
        intervalMinutes: 60 // 1시간
      }));
    }

    setEditingBot(null);
    setNewBot(defaultBot);
    setBacktestResult(null);
    setActiveTab('bots');
  };

  // 백테스팅 엔진 실행 핸들러
  const handleRunBacktest = async () => {
    if (newBot.algorithmType === 'REBALANCING') {
      alert('포트폴리오 리밸런싱 전략은 개별 종목 백테스팅을 지원하지 않습니다.');
      return;
    }
    if (!newBot.stockCode) {
      alert('백테스팅을 진행할 종목을 먼저 선택하거나 코드를 기입하세요.');
      return;
    }

    setBacktesting(true);
    setBacktestResult(null);

    let paramObj = {};
    if (newBot.algorithmType === 'ACCUMULATION') {
      paramObj = {
        amount: Number(algoParams.amount),
        intervalMinutes: Number(algoParams.intervalMinutes)
      };
    } else if (newBot.algorithmType === 'DROP_BUY') {
      paramObj = {
        amount: Number(algoParams.amount),
        dropPercent: Number(algoParams.dropPercent),
        lookbackDays: Number(algoParams.lookbackDays),
        targetProfitPercent: Number(algoParams.targetProfitPercent)
      };
    } else if (newBot.algorithmType === 'MA_CROSS') {
      paramObj = {
        amount: Number(algoParams.amount),
        shortPeriod: Number(algoParams.shortPeriod),
        longPeriod: Number(algoParams.longPeriod)
      };
    }

    try {
      const res = await axios.get(`${API_BASE}/backtest`, {
        params: {
          symbol: newBot.stockCode,
          algorithmType: newBot.algorithmType,
          parameters: JSON.stringify(paramObj)
        }
      });
      if (res.data.success) {
        setBacktestResult(res.data);
      } else {
        alert(res.data.message || '백테스팅 실패');
      }
    } catch (err) {
      console.error(err);
      alert('백테스팅 중 오류가 발생했습니다.');
    } finally {
      setBacktesting(false);
    }
  };

  // 리밸런싱 타겟 추가/삭제/변경 함수
  const addRebalanceTarget = () => {
    setRebalanceTargets([...rebalanceTargets, { code: '', name: '', weight: 0 }]);
  };
  const removeRebalanceTarget = (index) => {
    setRebalanceTargets(rebalanceTargets.filter((_, i) => i !== index));
  };
  const updateRebalanceTarget = (index, field, value) => {
    const updated = [...rebalanceTargets];
    updated[index][field] = value;
    setRebalanceTargets(updated);
  };

  // 봇 수정 모드 활성화 (수정 버튼 클릭 시)
  const handleStartEditBot = (bot) => {
    setEditingBot(bot);
    setNewBot({
      stockCode: bot.stockCode,
      stockName: bot.stockName,
      algorithmType: bot.algorithmType,
      active: bot.active
    });

    try {
      const params = JSON.parse(bot.parameters);
      if (params.quantity) {
        setTradeUnitType('quantity');
        setAlgoParams(prev => ({
          ...prev,
          quantity: params.quantity,
          intervalMinutes: params.intervalMinutes || prev.intervalMinutes,
          dropPercent: params.dropPercent || prev.dropPercent,
          lookbackDays: params.lookbackDays || prev.lookbackDays,
          targetProfitPercent: params.targetProfitPercent || prev.targetProfitPercent,
          shortPeriod: params.shortPeriod || prev.shortPeriod,
          longPeriod: params.longPeriod || prev.longPeriod
        }));
      } else {
        setTradeUnitType('amount');
        setAlgoParams(prev => ({
          ...prev,
          amount: params.amount || prev.amount,
          intervalMinutes: params.intervalMinutes || prev.intervalMinutes,
          dropPercent: params.dropPercent || prev.dropPercent,
          lookbackDays: params.lookbackDays || prev.lookbackDays,
          targetProfitPercent: params.targetProfitPercent || prev.targetProfitPercent,
          shortPeriod: params.shortPeriod || prev.shortPeriod,
          longPeriod: params.longPeriod || prev.longPeriod
        }));
      }
    } catch (e) {
      console.error('Failed to parse parameters for edit', e);
    }

    setRiskParams({
      useRiskControl: (bot.stopLossPercent || bot.takeProfitPercent) ? true : false,
      stopLossPercent: bot.stopLossPercent || 3.0,
      takeProfitPercent: bot.takeProfitPercent || 10.0
    });

    setBacktestResult(null);
    setActiveTab('bots');
  };

  // 봇 등록/수정 제출
  const handleSubmitBot = async (e) => {
    e.preventDefault();
    
    let paramObj = {};
    let finalCode = newBot.stockCode;
    let finalName = newBot.stockName;

    if (newBot.algorithmType === 'REBALANCING') {
      const sum = rebalanceTargets.reduce((acc, curr) => acc + Number(curr.weight), 0);
      if (sum !== 100) {
        alert(`포트폴리오 비중 합계는 정확히 100%여야 합니다. (현재: ${sum}%)`);
        return;
      }
      
      const targetsMap = {};
      rebalanceTargets.forEach(t => {
        targetsMap[t.code] = Number(t.weight);
      });
      paramObj = targetsMap;
      finalCode = 'PORTFOLIO';
      finalName = '자산 배분 포트폴리오';
    } else {
      if (newBot.algorithmType === 'ACCUMULATION') {
        if (tradeUnitType === 'quantity') {
          paramObj = {
            quantity: Number(algoParams.quantity),
            intervalMinutes: Number(algoParams.intervalMinutes)
          };
        } else {
          paramObj = {
            amount: Number(algoParams.amount),
            intervalMinutes: Number(algoParams.intervalMinutes)
          };
        }
      } else if (newBot.algorithmType === 'RSI_ACCUMULATE') {
        if (tradeUnitType === 'quantity') {
          paramObj = {
            quantity: Number(algoParams.quantity),
            rsiPeriod: Number(algoParams.rsiPeriod || 14),
            rsiThreshold: Number(algoParams.rsiThreshold || 30),
            intervalMinutes: Number(algoParams.intervalMinutes || 1440)
          };
        } else {
          paramObj = {
            amount: Number(algoParams.amount),
            rsiPeriod: Number(algoParams.rsiPeriod || 14),
            rsiThreshold: Number(algoParams.rsiThreshold || 30),
            intervalMinutes: Number(algoParams.intervalMinutes || 1440)
          };
        }
      } else if (newBot.algorithmType === 'DROP_BUY') {
        if (tradeUnitType === 'quantity') {
          paramObj = {
            quantity: Number(algoParams.quantity),
            dropPercent: Number(algoParams.dropPercent),
            lookbackDays: Number(algoParams.lookbackDays),
            targetProfitPercent: Number(algoParams.targetProfitPercent)
          };
        } else {
          paramObj = {
            amount: Number(algoParams.amount),
            dropPercent: Number(algoParams.dropPercent),
            lookbackDays: Number(algoParams.lookbackDays),
            targetProfitPercent: Number(algoParams.targetProfitPercent)
          };
        }
      } else if (newBot.algorithmType === 'MA_CROSS') {
        if (tradeUnitType === 'quantity') {
          paramObj = {
            quantity: Number(algoParams.quantity),
            shortPeriod: Number(algoParams.shortPeriod),
            longPeriod: Number(algoParams.longPeriod)
          };
        } else {
          paramObj = {
            amount: Number(algoParams.amount),
            shortPeriod: Number(algoParams.shortPeriod),
            longPeriod: Number(algoParams.longPeriod)
          };
        }
      }
    }

    const payload = {
      stockCode: finalCode,
      stockName: finalName,
      algorithmType: newBot.algorithmType,
      active: newBot.active,
      parameters: JSON.stringify(paramObj, null, 2),
      stopLossPercent: riskParams.useRiskControl && newBot.algorithmType !== 'REBALANCING' ? Number(riskParams.stopLossPercent) : null,
      takeProfitPercent: riskParams.useRiskControl && newBot.algorithmType !== 'REBALANCING' ? Number(riskParams.takeProfitPercent) : null
    };

    try {
      if (editingBot) {
        await axios.put(`${API_BASE}/bots/${editingBot.id}`, payload);
        setEditingBot(null);
      } else {
        await axios.post(`${API_BASE}/bots`, payload);
      }
      
      setNewBot({
        stockCode: '',
        stockName: '',
        algorithmType: 'ACCUMULATION',
        active: false
      });
      setBacktestResult(null);
      fetchData();
    } catch (err) {
      alert('봇 저장에 실패했습니다.');
    }
  };

  const handleToggleBot = async (id) => {
    try {
      await axios.post(`${API_BASE}/bots/${id}/toggle`);
      fetchData();
    } catch (err) {
      alert('봇 토글에 실패했습니다.');
    }
  };

  const handleDeleteBot = async (id) => {
    if (!confirm('정말 이 봇을 삭제하시겠습니까?')) return;
    try {
      await axios.delete(`${API_BASE}/bots/${id}`);
      fetchData();
    } catch (err) {
      alert('봇 삭제에 실패했습니다.');
    }
  };

  const getPieData = () => {
    if (!summary || !summary.latestAsset) return [];
    const cash = summary.latestAsset.cashBalance;
    const pieData = [{ name: '예수금 (Cash)', value: cash }];
    
    if (summary.holdings && summary.holdings.length > 0) {
      summary.holdings.forEach((item) => {
        const priceObj = prices.find(p => p.symbol === item.symbol);
        const curPrice = priceObj ? priceObj.lastPrice : 60000;
        pieData.push({
          name: `${item.name}`,
          value: item.quantity * curPrice
        });
      });
    } else if (summary.latestAsset.stockEvaluation > 0) {
      pieData.push({ name: '주식 평가액', value: summary.latestAsset.stockEvaluation });
    }

    return pieData;
  };

  const pieData = getPieData();
  const latestAsset = summary?.latestAsset || { cashBalance: 0, stockEvaluation: 0, totalAsset: 0 };
  const prevAsset = summary?.history && summary.history.length >= 2 
    ? summary.history[summary.history.length - 2].totalAsset 
    : latestAsset.totalAsset;
  const pnl = latestAsset.totalAsset - prevAsset;
  const pnlRate = prevAsset > 0 ? (pnl / prevAsset) * 100 : 0;

  // 일기장 캐러셀용 조작 함수
  const handlePrevDiary = () => {
    if (diaryIndex > 0) {
      setDiaryIndex(diaryIndex - 1);
    }
  };

  const handleNextDiary = () => {
    if (diaryIndex < diaries.length - 1) {
      setDiaryIndex(diaryIndex + 1);
    }
  };

  const activeDiary = diaries.length > 0 ? diaries[diaryIndex] : null;

  return (
    <div className="min-h-screen p-6 max-w-7xl mx-auto">
      {/* 실시간 매매 체결 토스트 알림창 */}
      {activeToast && (
        <div style={{
          position: 'fixed',
          top: '24px',
          left: '50%',
          transform: 'translateX(-50%)',
          zIndex: 9999,
          background: 'rgba(15, 23, 42, 0.95)',
          backdropFilter: 'blur(12px)',
          border: activeToast.type === 'BUY' ? '1px solid rgba(59, 130, 246, 0.4)' : '1px solid rgba(239, 68, 68, 0.4)',
          borderRadius: '12px',
          padding: '16px 20px',
          boxShadow: activeToast.type === 'BUY' 
            ? '0 10px 25px -5px rgba(59, 130, 246, 0.3)' 
            : '0 10px 25px -5px rgba(239, 68, 68, 0.3)',
          display: 'flex',
          alignItems: 'center',
          gap: '12px',
          animation: 'fadeInDown 0.4s ease-out',
          maxWidth: '90%',
          width: '450px'
        }}>
          <div style={{
            padding: '8px',
            borderRadius: '8px',
            background: activeToast.type === 'BUY' ? 'rgba(59, 130, 246, 0.15)' : 'rgba(239, 68, 68, 0.15)',
            color: activeToast.type === 'BUY' ? '#60a5fa' : '#f87171',
            display: 'flex'
          }}>
            <Bell className="w-5 h-5 animate-bounce" />
          </div>
          <span style={{ fontSize: '13px', fontWeight: '600', color: '#cbd5e1', flex: 1, lineHeight: '1.4' }}>
            {activeToast.message}
          </span>
          <button 
            onClick={() => setActiveToast(null)}
            style={{ border: 'none', background: 'transparent', color: '#64748b', cursor: 'pointer', padding: '4px', display: 'flex' }}
          >
            <X className="w-4 h-4 hover:text-slate-300" />
          </button>
        </div>
      )}

      {/* 수동 매수/매도 실행 퀵 모달 (자동완성 기능 결합) */}
      {showManualModal && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(0, 0, 0, 0.75)',
          backdropFilter: 'blur(4px)',
          zIndex: 9990,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center'
        }}>
          <div className="glass-card" style={{ width: '400px', padding: '24px', position: 'relative', border: '1px solid rgba(255,255,255,0.08)' }}>
            <button 
              onClick={() => {
                setShowManualModal(false);
                setManualSearchQuery('');
                setManualSearchResults([]);
              }}
              style={{ position: 'absolute', top: '16px', right: '16px', border: 'none', background: 'transparent', color: '#64748b', cursor: 'pointer' }}
            >
              <X className="w-5 h-5" />
            </button>
            <h3 style={{ margin: '0 0 16px 0', fontSize: '16px', fontWeight: 'bold', color: '#f1f5f9', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Activity className="w-5 h-5 text-indigo-400" />
              <span>⚡ 즉시 수동 주문 실행</span>
            </h3>
            
            <div style={{ marginBottom: '16px', paddingBottom: '12px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
              <label style={{ display: 'block', fontSize: '11px', color: '#818cf8', marginBottom: '6px', fontWeight: 'bold' }}>🔍 종목 초성/명칭 검색</label>
              <div className="dropdown-container">
                <div style={{ display: 'flex', alignItems: 'center', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '8px', padding: '0 10px' }}>
                  <Search className="w-4 h-4 text-slate-500" />
                  <input 
                    type="text" 
                    value={manualSearchQuery}
                    onChange={handleManualSearchChange}
                    placeholder="이름 또는 코드 입력 시 자동 완성"
                    style={{ flex: 1, background: 'transparent', border: 'none', padding: '10px 8px', color: '#f8fafc', outline: 'none', fontSize: '12.5px' }}
                  />
                </div>
                {manualSearchResults.length > 0 && (
                  <div className="dropdown-menu">
                    {manualSearchResults.map((item) => (
                      <div 
                        key={item.symbol || item.code} 
                        onClick={() => selectManualSearchItem(item)}
                        className="dropdown-item"
                      >
                        <span style={{ fontWeight: 'bold' }}>{item.name}</span>
                        <span style={{ color: '#818cf8', fontSize: '11px' }}>{item.symbol || item.code}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>

            <form onSubmit={handleManualTradeSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '11px', color: '#94a3b8', marginBottom: '6px' }}>구분</label>
                <div style={{ display: 'flex', gap: '12px' }}>
                  <label style={{ flex: 1, padding: '10px', borderRadius: '8px', border: manualOrder.orderType === 'BUY' ? '1px solid #3b82f6' : '1px solid rgba(255,255,255,0.05)', background: manualOrder.orderType === 'BUY' ? 'rgba(59,130,246,0.1)' : 'rgba(15,23,42,0.4)', textAlign: 'center', cursor: 'pointer', fontSize: '13px', fontWeight: 'bold', color: manualOrder.orderType === 'BUY' ? '#60a5fa' : '#64748b' }}>
                    <input 
                      type="radio" 
                      name="manualType" 
                      value="BUY" 
                      checked={manualOrder.orderType === 'BUY'} 
                      onChange={() => setManualOrder({ ...manualOrder, orderType: 'BUY' })}
                      style={{ display: 'none' }}
                    />
                    시장가 매수
                  </label>
                  <label style={{ flex: 1, padding: '10px', borderRadius: '8px', border: manualOrder.orderType === 'SELL' ? '1px solid #ef4444' : '1px solid rgba(255,255,255,0.05)', background: manualOrder.orderType === 'SELL' ? 'rgba(239,68,68,0.1)' : 'rgba(15,23,42,0.4)', textAlign: 'center', cursor: 'pointer', fontSize: '13px', fontWeight: 'bold', color: manualOrder.orderType === 'SELL' ? '#f87171' : '#64748b' }}>
                    <input 
                      type="radio" 
                      name="manualType" 
                      value="SELL" 
                      checked={manualOrder.orderType === 'SELL'} 
                      onChange={() => setManualOrder({ ...manualOrder, orderType: 'SELL' })}
                      style={{ display: 'none' }}
                    />
                    시장가 매도
                  </label>
                </div>
              </div>

              <div style={{ display: 'flex', gap: '12px' }}>
                <div style={{ flex: 1 }}>
                  <label style={{ display: 'block', fontSize: '11px', color: '#94a3b8', marginBottom: '6px' }}>종목명</label>
                  <div style={{ background: '#0b0f19', border: '1px solid rgba(255,255,255,0.08)', padding: '10px', borderRadius: '8px', color: '#94a3b8', fontSize: '12px', fontWeight: 'bold' }}>
                    {manualOrder.stockName}
                  </div>
                </div>
                <div style={{ flex: 1 }}>
                  <label style={{ display: 'block', fontSize: '11px', color: '#94a3b8', marginBottom: '6px' }}>종목코드</label>
                  <div style={{ background: '#0b0f19', border: '1px solid rgba(255,255,255,0.08)', padding: '10px', borderRadius: '8px', color: '#818cf8', fontSize: '12px', fontFamily: 'monospace' }}>
                    {manualOrder.stockCode}
                  </div>
                </div>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '11px', color: '#94a3b8', marginBottom: '6px' }}>주문 수량 (주)</label>
                <input 
                  type="number" 
                  value={manualOrder.quantity}
                  onChange={(e) => setManualOrder({ ...manualOrder, quantity: Number(e.target.value) })}
                  style={{ width: '92%', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.08)', padding: '10px', borderRadius: '8px', color: '#f8fafc', fontSize: '13px' }}
                  min="1"
                  required
                />
              </div>

              <button 
                type="submit" 
                style={{ width: '100%', border: 'none', background: manualOrder.orderType === 'BUY' ? '#2563eb' : '#dc2626', color: 'white', fontWeight: 'bold', padding: '12px 0', borderRadius: '8px', cursor: 'pointer', transition: 'opacity 0.2s', marginTop: '8px' }}
              >
                {manualOrder.orderType === 'BUY' ? '즉시 시장가 매수' : '즉시 시장가 매도'}
              </button>
            </form>
          </div>
        </div>
      )}

      {/* Header */}
      <header className="flex-row-between mb-8 pb-4 border-b border-slate-800">
        <div className="flex items-center space-x-3" style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <TrendingUp className="w-8 h-8 text-indigo-500" />
          <h1 className="text-2xl font-bold tracking-wider" style={{ margin: 0 }}>
            TOSS <span className="text-gradient">AUTO TRADER</span>
          </h1>
          <span className="px-3 py-1 text-xs font-semibold rounded-full bg-slate-800 border border-slate-700 animate-pulse-slow" style={{ padding: '4px 10px', borderRadius: '12px', border: '1px solid #1e293b' }}>
            {summary?.latestAsset ? 'ONLINE' : 'CONNECTING'}
          </span>
        </div>
        <div className="flex-row-between mt-4 md:mt-0" style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
          
          {/* 즉시 수동 매매 팝업 활성화 */}
          <button 
            onClick={() => {
              setManualOrder({
                stockCode: selectedStock.symbol,
                stockName: selectedStock.name,
                orderType: 'BUY',
                quantity: 1
              });
              setShowManualModal(true);
            }}
            style={{ display: 'flex', alignItems: 'center', gap: '6px', background: 'rgba(239, 68, 68, 0.1)', color: '#f87171', border: '1px solid rgba(239, 68, 68, 0.3)', padding: '8px 12px', borderRadius: '8px', fontSize: '12px', fontWeight: 'bold', cursor: 'pointer' }}
          >
            <Activity className="w-4 h-4" />
            수동 즉시 매매
          </button>

          <button 
            onClick={fetchData} 
            className="p-2 rounded-lg bg-slate-800 hover:bg-slate-700 transition"
            disabled={loading}
            style={{ padding: '8px', borderRadius: '8px', border: '1px solid #1e293b', background: '#1e293b', cursor: 'pointer' }}
          >
            <RefreshCw className={`w-5 h-5 ${loading ? 'animate-spin' : ''}`} />
          </button>
          <div className="tab-container">
            <button 
              onClick={() => setActiveTab('dashboard')} 
              className={`tab-btn ${activeTab === 'dashboard' ? 'active' : ''}`}
            >
              대시보드
            </button>
            <button 
              onClick={() => setActiveTab('bots')} 
              className={`tab-btn ${activeTab === 'bots' ? 'active' : ''}`}
            >
              봇 관리
            </button>
            <button 
              onClick={() => setActiveTab('history')} 
              className={`tab-btn ${activeTab === 'history' ? 'active' : ''}`}
            >
              주문 내역
            </button>
            <button 
              onClick={() => setActiveTab('ai-chat')} 
              className={`tab-btn ${activeTab === 'ai-chat' ? 'active' : ''}`}
            >
              💬 AI Q&A
            </button>
          </div>
        </div>
      </header>

      {error && (
        <div className="mb-6 p-4 rounded-xl border border-red-900 bg-red-950/30 text-red-400 flex items-center space-x-2" style={{ border: '1px solid #7f1d1d', background: 'rgba(127,29,29,0.2)', padding: '16px', borderRadius: '12px', color: '#fca5a5', display: 'flex', gap: '8px', alignItems: 'center' }}>
          <ShieldAlert className="w-5 h-5" />
          <span>{error}</span>
        </div>
      )}

      {/* 1. Dashboard Tab */}
      {activeTab === 'dashboard' && (
        <div className="space-y-6" style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
          
          <div className="dashboard-grid">
            {/* Left Column: Asset Summary Cards Stack */}
            <div className="summary-stack">
              <div className="info-card-compact">
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <div style={{ padding: '8px', borderRadius: '8px', background: 'rgba(99,102,241,0.15)', color: '#6366f1', display: 'flex' }}>
                    <Wallet className="w-5 h-5" />
                  </div>
                  <div>
                    <p style={{ margin: 0, fontSize: '10px', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>총 자산 (Total Assets)</p>
                    <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold' }}>₩ {latestAsset.totalAsset.toLocaleString()}</h3>
                  </div>
                </div>
              </div>

              <div className="info-card-compact">
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <div style={{ padding: '8px', borderRadius: '8px', background: 'rgba(16,185,129,0.15)', color: '#10b981', display: 'flex' }}>
                    <Activity className="w-5 h-5" />
                  </div>
                  <div>
                    <p style={{ margin: 0, fontSize: '10px', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>예수금 (Cash Balance)</p>
                    <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold' }}>₩ {latestAsset.cashBalance.toLocaleString()}</h3>
                  </div>
                </div>
              </div>

              <div className="info-card-compact">
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <div style={{ padding: '8px', borderRadius: '8px', background: 'rgba(236,72,153,0.15)', color: '#ec4899', display: 'flex' }}>
                    <Briefcase className="w-5 h-5" />
                  </div>
                  <div>
                    <p style={{ margin: 0, fontSize: '10px', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>주식 평가액 (Stocks)</p>
                    <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold' }}>₩ {latestAsset.stockEvaluation.toLocaleString()}</h3>
                  </div>
                </div>
              </div>

              <div className="info-card-compact">
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <div style={{ padding: '8px', borderRadius: '8px', background: pnl >= 0 ? 'rgba(239,68,68,0.15)' : 'rgba(59,130,246,0.15)', color: pnl >= 0 ? '#ef4444' : '#3b82f6', display: 'flex' }}>
                    <TrendingUp className="w-5 h-5" />
                  </div>
                  <div>
                    <p style={{ margin: 0, fontSize: '10px', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>전일 대비 손익 (PnL)</p>
                    <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold', color: pnl >= 0 ? '#fca5a5' : '#93c5fd' }}>
                      {pnl >= 0 ? '+' : ''}{pnl.toLocaleString()} ({pnlRate.toFixed(2)}%)
                    </h3>
                  </div>
                </div>
              </div>

              {/* Mini Portfolio Pie Chart */}
              <div className="glass-card" style={{ padding: '16px', flex: '1', display: 'flex', flexDirection: 'column', justifyContent: 'space-between', minHeight: '180px' }}>
                <span style={{ fontSize: '11px', fontWeight: '600', color: '#94a3b8', display: 'block', marginBottom: '8px' }}>자산 구성 비율</span>
                <div style={{ display: 'flex', alignItems: 'center', justifycontent: 'space-between' }}>
                  <div style={{ width: '90px', height: '90px' }}>
                    <ResponsiveContainer width="100%" height="100%">
                      <PieChart>
                        <Pie
                          data={pieData}
                          cx="50%"
                          cy="50%"
                          innerRadius={22}
                          outerRadius={38}
                          paddingAngle={3}
                          dataKey="value"
                        >
                          {pieData.map((entry, index) => (
                            <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                          ))}
                        </Pie>
                      </PieChart>
                    </ResponsiveContainer>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '10px', color: '#94a3b8', flex: '1', marginLeft: '12px', maxHeight: '100px', overflowY: 'auto' }}>
                    {pieData.map((entry, index) => (
                      <div key={`side-leg-${index}`} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                          <span style={{ display: 'inline-block', width: '8px', height: '8px', borderRadius: '50%', backgroundColor: COLORS[index % COLORS.length] }}></span>
                          <span style={{ display: 'inline-block', maxWidth: '75px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{entry.name}</span>
                        </span>
                        <span style={{ fontWeight: '600', color: '#cbd5e1' }}>₩{(entry.value / 10000).toFixed(0)}만</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>

            {/* Right Column: Dynamic Main Chart Area */}
            <div className="glass-card" style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
              <div>
                <div className="flex-row-between" style={{ paddingBottom: '16px', borderBottom: '1px solid rgba(255,255,255,0.06)', marginBottom: '20px' }}>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                      <h3 style={{ margin: 0, fontSize: '18px', fontWeight: 'bold', color: '#f1f5f9' }}>
                        {chartType === 'asset' ? '자산 변동 추이' : `${selectedStock.name} (${selectedStock.symbol}) 실시간 주가`}
                      </h3>
                      
                      {/* 차트 상단 헤더에 이 종목으로 봇 등록 단축 ➕ 버튼 구현 */}
                      {chartType === 'stock' && (
                        <button
                          onClick={() => handleQuickBotRegister(selectedStock.symbol, selectedStock.name)}
                          style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '4px',
                            background: 'rgba(99, 102, 241, 0.15)',
                            color: '#a5b4fc',
                            border: '1px solid rgba(99, 102, 241, 0.3)',
                            padding: '4px 10px',
                            borderRadius: '6px',
                            fontSize: '11px',
                            fontWeight: 'bold',
                            cursor: 'pointer'
                          }}
                        >
                          <Plus className="w-3.5 h-3.5" />
                          이 종목으로 봇 등록
                        </button>
                      )}
                    </div>
                    <p style={{ margin: '4px 0 0 0', fontSize: '12px', color: '#64748b' }}>
                      {chartType === 'asset' 
                        ? '거래 체결 시마다 미세하게 갱신되는 누적 가상자산 시계열 차트' 
                        : '토스 OpenAPI로부터 제공받은 종목의 실시간 시세 흐름'}
                    </p>
                  </div>
                  
                  <div className="tab-container" style={{ marginTop: '12px' }}>
                    <button
                      onClick={() => setChartType('asset')}
                      className={`tab-btn ${chartType === 'asset' ? 'active' : ''}`}
                    >
                      자산 추이
                    </button>
                    <button
                      onClick={() => {
                        if (stockCandles.length > 0) setChartType('stock');
                        else alert('아래 종목 리스트에서 종목을 클릭하여 차트를 로드하세요.');
                      }}
                      className={`tab-btn ${chartType === 'stock' ? 'active' : ''}`}
                    >
                      실시간 주가
                    </button>
                  </div>
                </div>

                <div style={{ height: '280px', width: '100%' }}>
                  {chartType === 'asset' ? (
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={summary?.history || []}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                        <XAxis 
                          dataKey="recordTime" 
                          stroke="#94a3b8" 
                          fontSize={10} 
                          tickFormatter={(val) => val ? val.substring(11, 16) : ''} // 시간/분 단위로 정밀 시각화
                        />
                        <YAxis stroke="#94a3b8" fontSize={11} tickFormatter={(val) => `₩${(val / 10000).toLocaleString()}만`} />
                        <Tooltip 
                          contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155' }}
                          formatter={(val) => [`₩${val.toLocaleString()}`, '총 자산']}
                          labelFormatter={(label) => `기록시간: ${label ? label.replace('T', ' ').substring(5, 16) : ''}`}
                        />
                        <Line type="monotone" dataKey="totalAsset" stroke="#6366f1" strokeWidth={3} dot={{ r: 3 }} activeDot={{ r: 6 }} />
                      </LineChart>
                    </ResponsiveContainer>
                  ) : (
                    <ResponsiveContainer width="100%" height="100%">
                      <AreaChart data={stockCandles}>
                        <defs>
                          <linearGradient id="colorPrice" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="#8b5cf6" stopOpacity={0.4}/>
                            <stop offset="95%" stopColor="#8b5cf6" stopOpacity={0}/>
                          </linearGradient>
                        </defs>
                        <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                        <XAxis 
                          dataKey="timestamp" 
                          stroke="#94a3b8" 
                          fontSize={10} 
                          tickFormatter={(val) => val.split('T')[0].substring(5)} 
                        />
                        <YAxis 
                          stroke="#94a3b8" 
                          fontSize={11} 
                          domain={['auto', 'auto']}
                          tickFormatter={(val) => `₩${val.toLocaleString()}`} 
                        />
                        <Tooltip 
                          contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155' }}
                          formatter={(val) => [`₩${val.toLocaleString()}`, '주가']}
                          labelFormatter={(label) => `날짜: ${label.split('T')[0]}`}
                        />
                        <Area type="monotone" dataKey="close" stroke="#a855f7" strokeWidth={3} fillOpacity={1} fill="url(#colorPrice)" />
                      </AreaChart>
                    </ResponsiveContainer>
                  )}
                </div>

                {/* 🤖 AI 투자위원회 & 여론 온도계 패널 */}
                {chartType === 'stock' && (
                  <div style={{ marginTop: '16px', paddingTop: '16px', borderTop: '1px solid rgba(255,255,255,0.06)' }}>
                    
                    {/* AI Committee Tabs */}
                    <div style={{ display: 'flex', gap: '8px', marginBottom: '12px', borderBottom: '1px solid rgba(255,255,255,0.05)', pb: '8px', overflowX: 'auto', paddingBottom: '8px' }}>
                      <button 
                        onClick={() => setAiTab('consensus')} 
                        style={{ fontSize: '11px', fontWeight: 'bold', padding: '4px 8px', borderRadius: '4px', border: 'none', background: aiTab === 'consensus' ? '#6366f1' : 'rgba(255,255,255,0.05)', color: aiTab === 'consensus' ? '#ffffff' : '#94a3b8', cursor: 'pointer' }}
                      >
                        ⚖️ 투자위 합의
                      </button>
                      <button 
                        onClick={() => setAiTab('gemini')} 
                        style={{ fontSize: '11px', fontWeight: 'bold', padding: '4px 8px', borderRadius: '4px', border: 'none', background: aiTab === 'gemini' ? '#6366f1' : 'rgba(255,255,255,0.05)', color: aiTab === 'gemini' ? '#ffffff' : '#94a3b8', cursor: 'pointer' }}
                      >
                        🤖 Gemini 상세
                      </button>
                      <button 
                        onClick={() => setAiTab('openai')} 
                        style={{ fontSize: '11px', fontWeight: 'bold', padding: '4px 8px', borderRadius: '4px', border: 'none', background: aiTab === 'openai' ? '#6366f1' : 'rgba(255,255,255,0.05)', color: aiTab === 'openai' ? '#ffffff' : '#94a3b8', cursor: 'pointer' }}
                      >
                        🧠 Llama 3.1 상세
                      </button>
                      <button 
                        onClick={() => setAiTab('sentiment')} 
                        style={{ fontSize: '11px', fontWeight: 'bold', padding: '4px 8px', borderRadius: '4px', border: 'none', background: aiTab === 'sentiment' ? '#6366f1' : 'rgba(255,255,255,0.05)', color: aiTab === 'sentiment' ? '#ffffff' : '#94a3b8', cursor: 'pointer' }}
                      >
                        🔥 개미 여론온도
                      </button>
                    </div>

                    {committeeLoading || aiLoading ? (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', padding: '12px', background: 'rgba(99,102,241,0.03)', borderRadius: '10px', border: '1px dashed rgba(99,102,241,0.2)', animation: 'pulse 1.5s infinite' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          <Cpu className="w-4 h-4 text-indigo-400 animate-spin" />
                          <span style={{ fontSize: '12px', color: '#a5b4fc', fontWeight: 'bold' }}>AI 투자위원회 위원들이 종목 정보를 교차 검증하는 중...</span>
                        </div>
                        <div style={{ height: '10px', background: 'rgba(255,255,255,0.05)', borderRadius: '4px', width: '90%' }}></div>
                        <div style={{ height: '10px', background: 'rgba(255,255,255,0.05)', borderRadius: '4px', width: '70%' }}></div>
                      </div>
                    ) : (
                      <>
                        {/* Tab 1: Consensus (투자위 합의) */}
                        {aiTab === 'consensus' && aiCommitteeReport && (
                          <div style={{ padding: '14px', background: 'rgba(15,23,42,0.4)', borderRadius: '12px', border: '1px solid #334155' }}>
                            <div className="flex-row-between" style={{ marginBottom: '12px' }}>
                              <span style={{ fontSize: '12px', fontWeight: 'bold', color: '#cbd5e1' }}>⚖️ AI 투자위원회 합의 시그널</span>
                              <span style={{
                                padding: '3px 8px',
                                borderRadius: '8px',
                                fontSize: '11px',
                                fontWeight: 'bold',
                                background: aiCommitteeReport.consensusRating === 'RECOMMEND' ? 'rgba(16,185,129,0.15)' : aiCommitteeReport.consensusRating === 'WARNING' ? 'rgba(239,68,68,0.15)' : 'rgba(245,158,11,0.15)',
                                color: aiCommitteeReport.consensusRating === 'RECOMMEND' ? '#34d399' : aiCommitteeReport.consensusRating === 'WARNING' ? '#f87171' : '#fbbf24',
                                border: aiCommitteeReport.consensusRating === 'RECOMMEND' ? '1px solid rgba(16,185,129,0.3)' : aiCommitteeReport.consensusRating === 'WARNING' ? '1px solid rgba(239,68,68,0.3)' : '1px solid rgba(245,158,11,0.3)'
                              }}>
                                {aiCommitteeReport.consensusRating === 'RECOMMEND' ? '합의 매수추천' : aiCommitteeReport.consensusRating === 'WARNING' ? '합의 매도경고' : '의견 보류/관망'}
                              </span>
                            </div>

                            {/* Consensus Gauge Progress bar */}
                            <div style={{ marginBottom: '12px' }}>
                              <div className="flex-row-between" style={{ fontSize: '11px', color: '#94a3b8', marginBottom: '4px' }}>
                                <span>합의 신뢰도 점수 (Consensus Score)</span>
                                <span style={{ color: '#6366f1', fontWeight: 'bold' }}>{aiCommitteeReport.consensusScore}점</span>
                              </div>
                              <div style={{ height: '8px', background: '#0f172a', borderRadius: '4px', overflow: 'hidden', display: 'flex' }}>
                                <div style={{ width: `${aiCommitteeReport.consensusScore}%`, background: 'linear-gradient(90deg, #6366f1 0%, #10b981 100%)', borderRadius: '4px' }}></div>
                              </div>
                            </div>

                            <p style={{ margin: '0 0 10px 0', fontSize: '11.5px', color: '#cbd5e1', lineHeight: '1.5' }}>
                              {aiCommitteeReport.mode === 'DUAL' 
                                ? '본 결과는 Gemini AI와 ChatGPT의 의견을 종합하여 산출된 합의값입니다. 양측 AI의 시그널이 일치할 때 신뢰도가 가장 높습니다.'
                                : '현재 OpenAI API 키가 설정되지 않아 Gemini AI 단독 의견으로 시그널을 제공하고 있습니다.'}
                            </p>
                          </div>
                        )}

                        {/* Tab 2: Gemini 상세 */}
                        {aiTab === 'gemini' && aiReport && (
                          <div style={{ padding: '14px', background: 'rgba(15,23,42,0.4)', borderRadius: '12px', border: '1px solid rgba(16,185,129,0.2)' }}>
                            <div className="flex-row-between" style={{ marginBottom: '10px' }}>
                              <span style={{ fontSize: '11.5px', fontWeight: 'bold', color: '#10b981' }}>🤖 Gemini 기술적 차트 분석</span>
                              <span style={{ fontSize: '10.5px', fontWeight: 'bold', color: aiReport.rating === 'RECOMMEND' ? '#34d399' : '#f87171' }}>
                                {aiReport.rating}
                              </span>
                            </div>
                            <p style={{ margin: '0 0 8px 0', fontSize: '11.5px', color: '#cbd5e1', lineHeight: '1.5' }}>{aiReport.reason}</p>
                            <div style={{ fontSize: '11px', color: '#94a3b8' }}>🎯 추천가: ₩ {aiReport.recommendedPrice?.toLocaleString()}원</div>
                          </div>
                        )}

                        {/* Tab 3: Llama 3.1 상세 */}
                        {aiTab === 'openai' && (
                          <div style={{ padding: '14px', background: 'rgba(15,23,42,0.4)', borderRadius: '12px', border: '1px solid rgba(99,102,241,0.2)' }}>
                            {aiCommitteeReport?.openai ? (
                              <div>
                                <div className="flex-row-between" style={{ marginBottom: '10px' }}>
                                  <span style={{ fontSize: '11.5px', fontWeight: 'bold', color: '#818cf8' }}>🧠 Meta Llama 3.1 70B 분석</span>
                                  <span style={{ fontSize: '10.5px', fontWeight: 'bold', color: String(aiCommitteeReport.openai.rating) === 'RECOMMEND' ? '#34d399' : '#f87171' }}>
                                    {aiCommitteeReport.openai.rating}
                                  </span>
                                </div>
                                <p style={{ margin: '0 0 8px 0', fontSize: '11.5px', color: '#cbd5e1', lineHeight: '1.5' }}>{aiCommitteeReport.openai.reason}</p>
                                <div style={{ fontSize: '11px', color: '#94a3b8' }}>🎯 추천가: ₩ {Number(aiCommitteeReport.openai.recommendedPrice)?.toLocaleString()}원</div>
                              </div>
                            ) : (
                              <div style={{ textAlign: 'center', padding: '12px', fontSize: '11.5px', color: '#64748b' }}>
                                💡 .env 파일에 <strong>GROQ_API_KEY</strong>를 등록하시면,<br />
                                Llama 3.1의 차트 검증 판단 의견을 함께 받아볼 수 있습니다!
                              </div>
                            )}
                          </div>
                        )}

                        {/* Tab 4: 개미 여론 온도 */}
                        {aiTab === 'sentiment' && (
                          <div style={{ padding: '14px', background: 'rgba(15,23,42,0.4)', borderRadius: '12px', border: '1px solid #334155' }}>
                            {sentimentGauge ? (
                              <div>
                                <div className="flex-row-between" style={{ marginBottom: '8px' }}>
                                  <span style={{ fontSize: '12px', fontWeight: 'bold', color: '#f1f5f9' }}>🔥 주주 광기 vs 공포 온도계</span>
                                  <span style={{ fontSize: '11px', color: '#fbbf24', fontWeight: 'bold' }}>심리지수 {sentimentGauge.score}도</span>
                                </div>

                                {/* Sentiment Gauge Progress bar */}
                                <div style={{ height: '14px', background: '#3b82f6', borderRadius: '7px', overflow: 'hidden', display: 'flex', marginBottom: '12px', border: '1px solid #1e293b' }}>
                                  <div style={{ width: `${sentimentGauge.positivePercent}%`, background: '#10b981', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '9px', fontWeight: 'bold', color: '#ffffff' }}>
                                    {sentimentGauge.positivePercent}% 희망
                                  </div>
                                  <div style={{ width: `${sentimentGauge.negativePercent}%`, background: '#ef4444', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '9px', fontWeight: 'bold', color: '#ffffff' }}>
                                    {sentimentGauge.negativePercent}% 공포
                                  </div>
                                </div>

                                <p style={{ margin: 0, fontSize: '11.5px', color: '#94a3b8', lineHeight: '1.5' }}>
                                  {sentimentGauge.summary}
                                </p>
                              </div>
                            ) : (
                              <div style={{ textAlign: 'center', padding: '12px', fontSize: '11.5px', color: '#64748b' }}>
                                📰 실시간 뉴스 여론 기사를 파싱하는 중입니다...
                              </div>
                            )}
                          </div>
                        )}
                      </>
                    )}

                  </div>
                )}

              </div>
            </div>
          </div>

          {/* 💼 나의 가상 주식 보유 현황 (My Holdings) */}
          <div className="glass-card" style={{ padding: '24px' }}>
            <div className="flex-row-between" style={{ marginBottom: '16px' }}>
              <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Briefcase className="w-5 h-5 text-emerald-400" />
                <span>💼 나의 가상 주식 보유 잔고 현황</span>
              </h3>
              <span style={{ fontSize: '11px', color: '#94a3b8' }}>
                * 보유 종목의 평단가 대비 평가 손익률을 실시간으로 집계합니다.
              </span>
            </div>

            {summary?.holdings && summary.holdings.length > 0 ? (
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px', color: '#cbd5e1' }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.08)', color: '#94a3b8', fontWeight: '600', background: 'rgba(15,23,42,0.3)' }}>
                      <th style={{ padding: '10px 12px' }}>종목명</th>
                      <th style={{ padding: '10px 12px', textAlign: 'right' }}>보유 수량</th>
                      <th style={{ padding: '10px 12px', textAlign: 'right' }}>평균 매수단가</th>
                      <th style={{ padding: '10px 12px', textAlign: 'right' }}>현재가</th>
                      <th style={{ padding: '10px 12px', textAlign: 'right' }}>평가 금액</th>
                      <th style={{ padding: '10px 12px', textAlign: 'right' }}>평가 손익 (수익률)</th>
                      <th style={{ padding: '10px 12px', textAlign: 'center' }}>청산</th>
                    </tr>
                  </thead>
                  <tbody>
                    {summary.holdings.map((item) => {
                      const priceObj = prices.find(p => p.symbol === item.symbol);
                      const curPrice = priceObj ? priceObj.lastPrice : 60000;
                      const avgPrice = item.avgPrice || 0;
                      const totalBuyCost = item.quantity * avgPrice;
                      const totalEvalAmount = item.quantity * curPrice;
                      const pnlAmount = totalEvalAmount - totalBuyCost;
                      const pnlRate = totalBuyCost > 0 ? (pnlAmount / totalBuyCost) * 100 : 0;

                      return (
                        <tr key={item.symbol} style={{ borderBottom: '1px solid rgba(255,255,255,0.05)', transition: 'background 0.2s' }} className="table-row-hover">
                          <td style={{ padding: '12px', fontWeight: 'bold', color: '#f1f5f9' }}>
                            <span 
                              onClick={() => fetchStockCandles(item.symbol, item.name)} 
                              style={{ cursor: 'pointer', textDecoration: 'underline', textUnderlineOffset: '4px' }}
                            >
                              {item.name}
                            </span>
                            <span style={{ fontSize: '11px', color: '#64748b', fontWeight: 'normal', marginLeft: '6px' }}>({item.symbol})</span>
                          </td>
                          <td style={{ padding: '12px', textAlign: 'right', fontFamily: 'monospace', fontWeight: 'bold' }}>
                            {item.quantity.toLocaleString()} 주
                          </td>
                          <td style={{ padding: '12px', textAlign: 'right', fontFamily: 'monospace' }}>
                            ₩ {avgPrice.toLocaleString()}
                          </td>
                          <td style={{ padding: '12px', textAlign: 'right', fontFamily: 'monospace', color: '#a5b4fc' }}>
                            ₩ {curPrice.toLocaleString()}
                          </td>
                          <td style={{ padding: '12px', textAlign: 'right', fontFamily: 'monospace', fontWeight: '600' }}>
                            ₩ {totalEvalAmount.toLocaleString()}
                          </td>
                          <td style={{ 
                            padding: '12px', 
                            textAlign: 'right', 
                            fontFamily: 'monospace', 
                            fontWeight: 'bold', 
                            color: pnlAmount >= 0 ? '#fca5a5' : '#93c5fd' 
                          }}>
                            {pnlAmount >= 0 ? '+' : ''}{pnlAmount.toLocaleString()}원
                            <span style={{ fontSize: '11px', marginLeft: '6px' }}>
                              ({pnlAmount >= 0 ? '+' : ''}{pnlRate.toFixed(2)}%)
                            </span>
                          </td>
                          <td style={{ padding: '12px', textAlign: 'center' }}>
                            <button
                              onClick={() => {
                                setManualOrder({
                                  stockCode: item.symbol,
                                  stockName: item.name,
                                  orderType: 'SELL',
                                  quantity: item.quantity
                                });
                                setShowManualModal(true);
                              }}
                              style={{ 
                                border: 'none', 
                                background: 'rgba(239, 68, 68, 0.15)', 
                                color: '#f87171', 
                                padding: '4px 10px', 
                                borderRadius: '6px', 
                                fontSize: '11px', 
                                fontWeight: 'bold', 
                                cursor: 'pointer' 
                              }}
                              title="보유 주식 즉시 시장가 매도 청산"
                            >
                              ⚡ 매도
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            ) : (
              <div style={{ padding: '24px 12px', textAlign: 'center', fontSize: '13.5px', color: '#64748b' }}>
                💼 현재 보유 중인 가상 주식 잔고가 존재하지 않습니다. 수동 즉시 매매 혹은 자동 감시 봇을 가동해보세요!
              </div>
            )}
          </div>

          {/* 🏆 전략 배틀 아레나 리더보드 */}
          <div className="glass-card" style={{ padding: '24px' }}>
            <h3 style={{ margin: '0 0 16px 0', fontSize: '16px', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <TrendingUp className="w-5 h-5 text-yellow-400" />
              <span>🏆 전략 배틀 아레나 (최근 15일 모의 랭킹)</span>
            </h3>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px', color: '#cbd5e1' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.08)', color: '#94a3b8', fontWeight: '600', background: 'rgba(15,23,42,0.3)' }}>
                    <th style={{ padding: '10px 12px' }}>순위</th>
                    <th style={{ padding: '10px 12px' }}>구동 전략</th>
                    <th style={{ padding: '10px 12px' }}>시뮬레이션 종목</th>
                    <th style={{ padding: '10px 12px', textAlign: 'right' }}>수익률 (%)</th>
                    <th style={{ padding: '10px 12px', textAlign: 'right' }}>총 거래횟수</th>
                    <th style={{ padding: '10px 12px', textAlign: 'right' }}>익절 성공률</th>
                  </tr>
                </thead>
                <tbody>
                  {arenaRanking.map((item, index) => (
                    <tr key={index} style={{ borderBottom: '1px solid rgba(255,255,255,0.05)', background: index === 0 ? 'rgba(234,179,8,0.04)' : 'transparent' }}>
                      <td style={{ padding: '12px', fontWeight: 'bold', color: index === 0 ? '#facc15' : '#cbd5e1' }}>
                        {index === 0 ? '👑 1위' : `${index + 1}위`}
                      </td>
                      <td style={{ padding: '12px', fontWeight: 'bold', color: '#ffffff' }}>{item.strategy}</td>
                      <td style={{ padding: '12px' }}>{item.stockName} ({item.stockCode})</td>
                      <td style={{ padding: '12px', textAlign: 'right', fontWeight: 'bold', color: item.profitRate >= 0 ? '#fca5a5' : '#93c5fd', fontFamily: 'monospace' }}>
                        {item.profitRate >= 0 ? '+' : ''}{item.profitRate}%
                      </td>
                      <td style={{ padding: '12px', textAlign: 'right', fontFamily: 'monospace' }}>{item.totalTrades}회</td>
                      <td style={{ padding: '12px', textAlign: 'right', fontFamily: 'monospace', color: '#34d399' }}>{item.winRate}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Real-time watch prices */}
          <div className="glass-card" style={{ padding: '24px' }}>
            <div className="flex-row-between" style={{ marginBottom: '16px' }}>
              <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Activity className="w-5 h-5 text-indigo-400" />
                <span>실시간 감시 종목 시세 판넬</span>
              </h3>
              <span style={{ fontSize: '10px', color: '#64748b' }}>* 종목 카드를 클릭 시 차트가 변경되며, 번개 아이콘(⚡) 클릭 시 수동 매매 팝업이 뜹니다.</span>
            </div>
            
            <div className="stock-grid">
              {prices.map((p) => {
                const botObj = bots.find(b => b.stockCode === p.symbol);
                const isSelected = selectedStock.symbol === p.symbol && chartType === 'stock';
                return (
                  <div 
                    key={p.symbol} 
                    className="p-4 rounded-xl border transition flex justify-between items-center"
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      padding: '16px',
                      borderRadius: '12px',
                      border: isSelected ? '1px solid #6366f1' : '1px solid rgba(255,255,255,0.06)',
                      background: isSelected ? 'rgba(99,102,241,0.08)' : 'rgba(15,23,42,0.4)',
                      transition: 'all 0.2s ease'
                    }}
                  >
                    <div onClick={() => fetchStockCandles(p.symbol, botObj ? botObj.stockName : '관심 종목')} style={{ cursor: 'pointer', flex: 1 }}>
                      <h4 style={{ margin: 0, fontSize: '14px', fontWeight: 'bold', color: '#f1f5f9' }}>
                        {botObj?.stockName || '종목'}
                      </h4>
                      <span style={{ fontSize: '10px', color: '#64748b' }}>{p.symbol}</span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', textAlign: 'right' }}>
                      <div onClick={() => fetchStockCandles(p.symbol, botObj ? botObj.stockName : '관심 종목')} style={{ cursor: 'pointer' }}>
                        <span style={{ display: 'block', fontFamily: 'monospace', fontSize: '15px', fontWeight: 'bold', color: '#818cf8' }}>
                          ₩ {p.lastPrice ? p.lastPrice.toLocaleString() : '---'}
                        </span>
                        <span style={{ display: 'block', fontSize: '9px', color: '#64748b' }}>
                          {p.timestamp ? new Date(p.timestamp).toLocaleTimeString() : '실시간'}
                        </span>
                      </div>
                      
                      {/* ⚡ 수동 주문 트리거 직결 버튼 */}
                      <button
                        onClick={() => {
                          setManualOrder({
                            stockCode: p.symbol,
                            stockName: botObj ? botObj.stockName : '관심 종목',
                            orderType: 'BUY',
                            quantity: 1
                          });
                          setShowManualModal(true);
                        }}
                        style={{ border: 'none', background: 'rgba(239, 68, 68, 0.15)', color: '#f87171', padding: '6px', borderRadius: '6px', cursor: 'pointer', display: 'flex' }}
                        title="수동 매매 주문 실행"
                      >
                        <Activity className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Real-time Hot Stock Market Trends Section */}
          <div className="glass-card" style={{ padding: '24px' }}>
            <div className="flex-row-between" style={{ marginBottom: '20px' }}>
              <h3 style={{ margin: 0, fontSize: '18px', fontWeight: 'bold', color: '#f1f5f9' }}>
                📈 실시간 국내 주식 시장 트렌드
              </h3>
              <div style={{ display: 'flex', alignItems: 'center', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '8px', padding: '0 10px', width: '280px' }}>
                <Search className="w-4 h-4 text-slate-500" />
                <input 
                  type="text" 
                  value={dashSearchQuery}
                  onChange={handleDashSearchChange}
                  placeholder="종목 이름/코드 검색 (예: 현대, 삼성)"
                  style={{ flex: 1, background: 'transparent', border: 'none', padding: '8px 6px', color: '#f8fafc', outline: 'none', fontSize: '12px' }}
                />
              </div>
            </div>

            {dashSearchResults.length > 0 && (
              <div className="hot-list-container" style={{ marginBottom: '20px', border: '1px solid rgba(99, 102, 241, 0.3)', background: 'rgba(99, 102, 241, 0.03)' }}>
                <h4 style={{ margin: '0 0 12px 0', fontSize: '14px', fontWeight: 'bold', color: '#818cf8', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  🔍 검색 결과 ({dashSearchResults.length} 건)
                </h4>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '12px' }}>
                  {dashSearchResults.map((item) => (
                    <div key={item.symbol} className="hot-list-item" style={{ background: 'rgba(15, 23, 42, 0.6)' }}>
                      <div>
                        <span style={{ fontWeight: 'bold', fontSize: '13px', display: 'block', color: '#cbd5e1' }}>{item.name}</span>
                        <span style={{ fontSize: '9px', color: '#64748b' }}>{item.symbol}</span>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <div style={{ textAlign: 'right' }}>
                          <span style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#f1f5f9' }}>₩{item.lastPrice.toLocaleString()}</span>
                          <span style={{ fontSize: '11px', color: item.changePercent >= 0 ? '#fca5a5' : '#93c5fd', fontWeight: 'bold' }}>
                            {item.changePercent >= 0 ? '+' : ''}{item.changePercent}%
                          </span>
                        </div>
                        <div style={{ display: 'flex', gap: '4px' }}>
                          <button 
                            onClick={() => fetchStockCandles(item.symbol, item.name)}
                            style={{ padding: '6px', border: 'none', background: 'rgba(99,102,241,0.15)', color: '#818cf8', borderRadius: '6px', cursor: 'pointer', display: 'flex' }}
                          >
                            <Eye className="w-3.5 h-3.5" />
                          </button>
                          <button 
                            onClick={() => {
                              setManualOrder({
                                stockCode: item.symbol,
                                stockName: item.name,
                                orderType: 'BUY',
                                quantity: 1
                              });
                              setShowManualModal(true);
                            }}
                            style={{ padding: '6px', border: 'none', background: 'rgba(239,68,68,0.15)', color: '#fca5a5', borderRadius: '6px', cursor: 'pointer', display: 'flex' }}
                            title="즉시 수동매매"
                          >
                            <Activity className="w-3.5 h-3.5" />
                          </button>
                          <button 
                            onClick={() => handleQuickBotRegister(item.symbol, item.name)}
                            style={{ padding: '6px', border: 'none', background: 'rgba(16,185,129,0.15)', color: '#34d399', borderRadius: '6px', cursor: 'pointer', display: 'flex' }}
                          >
                            <Plus className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
            
            <div className="hot-category-grid">
              {/* Category 1: Gainers */}
              <div className="hot-list-container">
                <h4 style={{ margin: '0 0 12px 0', fontSize: '14px', fontWeight: 'bold', color: '#ef4444', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  🔥 급상승 종목
                </h4>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  {hotStocks.gainers?.map((item) => (
                    <div key={item.symbol} className="hot-list-item">
                      <div>
                        <span style={{ fontWeight: 'bold', fontSize: '13px', display: 'block', color: '#cbd5e1' }}>{item.name}</span>
                        <span style={{ fontSize: '9px', color: '#64748b' }}>{item.symbol}</span>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <div style={{ textAlign: 'right' }}>
                          <span style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#f1f5f9' }}>₩{item.lastPrice.toLocaleString()}</span>
                          <span style={{ fontSize: '11px', color: '#fca5a5', fontWeight: 'bold' }}>+{item.changePercent}%</span>
                        </div>
                        <div style={{ display: 'flex', gap: '4px' }}>
                          <button 
                            onClick={() => fetchStockCandles(item.symbol, item.name)}
                            style={{ padding: '6px', border: 'none', background: 'rgba(99,102,241,0.15)', color: '#818cf8', borderRadius: '6px', cursor: 'pointer', display: 'flex' }}
                          >
                            <Eye className="w-3.5 h-3.5" />
                          </button>
                          <button 
                            onClick={() => {
                              setManualOrder({
                                stockCode: item.symbol,
                                stockName: item.name,
                                orderType: 'BUY',
                                quantity: 1
                              });
                              setShowManualModal(true);
                            }}
                            style={{ padding: '6px', border: 'none', background: 'rgba(239,68,68,0.15)', color: '#fca5a5', borderRadius: '6px', cursor: 'pointer', display: 'flex' }}
                          >
                            <Activity className="w-3.5 h-3.5" />
                          </button>
                          <button 
                            onClick={() => handleQuickBotRegister(item.symbol, item.name)}
                            style={{ padding: '6px', border: 'none', background: 'rgba(16,185,129,0.15)', color: '#34d399', borderRadius: '6px', cursor: 'pointer', display: 'flex' }}
                          >
                            <Plus className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Category 2: Losers */}
              <div className="hot-list-container">
                <h4 style={{ margin: '0 0 12px 0', fontSize: '14px', fontWeight: 'bold', color: '#3b82f6', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  📉 급하락 종목
                </h4>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  {hotStocks.losers?.map((item) => (
                    <div key={item.symbol} className="hot-list-item">
                      <div>
                        <span style={{ fontWeight: 'bold', fontSize: '13px', display: 'block', color: '#cbd5e1' }}>{item.name}</span>
                        <span style={{ fontSize: '9px', color: '#64748b' }}>{item.symbol}</span>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <div style={{ textAlign: 'right' }}>
                          <span style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#f1f5f9' }}>₩{item.lastPrice.toLocaleString()}</span>
                          <span style={{ fontSize: '11px', color: '#93c5fd', fontWeight: 'bold' }}>{item.changePercent}%</span>
                        </div>
                        <div style={{ display: 'flex', gap: '4px' }}>
                          <button 
                            onClick={() => fetchStockCandles(item.symbol, item.name)}
                            style={{ padding: '6px', border: 'none', background: 'rgba(99,102,241,0.15)', color: '#818cf8', borderRadius: '6px', cursor: 'pointer', display: 'flex' }}
                          >
                            <Eye className="w-3.5 h-3.5" />
                          </button>
                          <button 
                            onClick={() => {
                              setManualOrder({
                                stockCode: item.symbol,
                                stockName: item.name,
                                orderType: 'BUY',
                                quantity: 1
                              });
                              setShowManualModal(true);
                            }}
                            style={{ padding: '6px', border: 'none', background: 'rgba(239,68,68,0.15)', color: '#fca5a5', borderRadius: '6px', cursor: 'pointer', display: 'flex' }}
                          >
                            <Activity className="w-3.5 h-3.5" />
                          </button>
                          <button 
                            onClick={() => handleQuickBotRegister(item.symbol, item.name)}
                            style={{ padding: '6px', border: 'none', background: 'rgba(16,185,129,0.15)', color: '#34d399', borderRadius: '6px', cursor: 'pointer', display: 'flex' }}
                          >
                            <Plus className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Category 3: Volumes */}
              <div className="hot-list-container">
                <h4 style={{ margin: '0 0 12px 0', fontSize: '14px', fontWeight: 'bold', color: '#eab308', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  📊 거래량 상위 종목
                </h4>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  {hotStocks.volumes?.map((item) => (
                    <div key={item.symbol} className="hot-list-item">
                      <div>
                        <span style={{ fontWeight: 'bold', fontSize: '13px', display: 'block', color: '#cbd5e1' }}>{item.name}</span>
                        <span style={{ fontSize: '9px', color: '#64748b' }}>{item.symbol}</span>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <div style={{ textAlign: 'right' }}>
                          <span style={{ display: 'block', fontSize: '13px', fontWeight: '600', color: '#f1f5f9' }}>₩{item.lastPrice.toLocaleString()}</span>
                          <span style={{ fontSize: '10px', color: '#94a3b8' }}>{(item.volume / 10000).toFixed(0)}만 주</span>
                        </div>
                        <div style={{ display: 'flex', gap: '4px' }}>
                          <button 
                            onClick={() => fetchStockCandles(item.symbol, item.name)}
                            style={{ padding: '6px', border: 'none', background: 'rgba(99,102,241,0.15)', color: '#818cf8', borderRadius: '6px', cursor: 'pointer', display: 'flex' }}
                          >
                            <Eye className="w-3.5 h-3.5" />
                          </button>
                          <button 
                            onClick={() => {
                              setManualOrder({
                                stockCode: item.symbol,
                                stockName: item.name,
                                orderType: 'BUY',
                                quantity: 1
                              });
                              setShowManualModal(true);
                            }}
                            style={{ padding: '6px', border: 'none', background: 'rgba(239,68,68,0.15)', color: '#fca5a5', borderRadius: '6px', cursor: 'pointer', display: 'flex' }}
                          >
                            <Activity className="w-3.5 h-3.5" />
                          </button>
                          <button 
                            onClick={() => handleQuickBotRegister(item.symbol, item.name)}
                            style={{ padding: '6px', border: 'none', background: 'rgba(16,185,129,0.15)', color: '#34d399', borderRadius: '6px', cursor: 'pointer', display: 'flex' }}
                          >
                            <Plus className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>

          {/* 💬 텔레그램 스마트 원격 제어 & 알림 연동 위젯 */}
          <div className="glass-card" style={{ padding: '20px 24px', borderLeft: '4px solid #10b981', background: 'rgba(16,185,129,0.02)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ margin: 0, fontSize: '15px', fontWeight: 'bold', color: '#f1f5f9', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Send className="w-4 h-4 text-emerald-400" />
                <span>💬 텔레그램 스마트 원격 제어 가이드</span>
              </h3>
              <span style={{ fontSize: '10px', background: 'rgba(16,185,129,0.12)', color: '#34d399', padding: '2px 8px', borderRadius: '12px', border: '1px solid rgba(16,185,129,0.25)' }}>
                웹훅 연동 완료
              </span>
            </div>
            <p style={{ margin: '8px 0 12px 0', fontSize: '12px', color: '#94a3b8', lineHeight: '1.4' }}>
              외부에서 스마트폰 텔레그램 방을 통해 봇 제어 및 자산 현황을 2-Way로 모니터링할 수 있습니다. 아래의 단축 명령어를 사용하세요.
            </p>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px' }}>
              <div style={{ background: '#0b0f19', padding: '8px 12px', borderRadius: '8px', border: '1px solid rgba(255,255,255,0.05)', fontSize: '11px', flex: '1', minWidth: '180px' }}>
                <strong style={{ color: '#60a5fa', fontFamily: 'monospace' }}>/status</strong>
                <span style={{ color: '#cbd5e1', marginLeft: '6px' }}>가용 예수금 및 감시 봇 실시간 요약</span>
              </div>
              <div style={{ background: '#0b0f19', padding: '8px 12px', borderRadius: '8px', border: '1px solid rgba(255,255,255,0.05)', fontSize: '11px', flex: '1', minWidth: '180px' }}>
                <strong style={{ color: '#fbbf24', fontFamily: 'monospace' }}>/stop &#123;종목코드&#125;</strong>
                <span style={{ color: '#cbd5e1', marginLeft: '6px' }}>원격 특정 봇 정지</span>
              </div>
              <div style={{ background: '#0b0f19', padding: '8px 12px', borderRadius: '8px', border: '1px solid rgba(255,255,255,0.05)', fontSize: '11px', flex: '1', minWidth: '180px' }}>
                <strong style={{ color: '#f87171', fontFamily: 'monospace' }}>/sellall</strong>
                <span style={{ color: '#cbd5e1', marginLeft: '6px' }}>전체 봇 정지 및 전량 강제 청산</span>
              </div>
            </div>
          </div>

          {/* ✍️ AI 트레이딩 복기 일지 (오답노트 - 캐러셀 이력 기능 탑재) */}
          <div className="glass-card" style={{ padding: '24px', borderLeft: '4px solid #6366f1', background: 'rgba(99,102,241,0.02)' }}>
            <div className="flex-row-between" style={{ marginBottom: '12px' }}>
              <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold', color: '#f1f5f9', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <PenTool className="w-5 h-5 text-indigo-400" />
                <span>✍️ Gemini AI 일일 투자 피드백 일지 (오답노트)</span>
              </h3>
              
              {/* 캐러셀 조작 화살표 추가 */}
              {diaries.length > 1 && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <button 
                    onClick={handlePrevDiary} 
                    disabled={diaryIndex === 0}
                    style={{ border: 'none', background: 'rgba(255,255,255,0.05)', color: diaryIndex === 0 ? '#475569' : '#cbd5e1', padding: '6px 10px', borderRadius: '6px', cursor: diaryIndex === 0 ? 'not-allowed' : 'pointer', display: 'flex', alignItems: 'center' }}
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                  <span style={{ fontSize: '11.5px', color: '#94a3b8', fontWeight: 'bold', minWidth: '40px', textAlign: 'center' }}>
                    {diaryIndex + 1} / {diaries.length}
                  </span>
                  <button 
                    onClick={handleNextDiary} 
                    disabled={diaryIndex === diaries.length - 1}
                    style={{ border: 'none', background: 'rgba(255,255,255,0.05)', color: diaryIndex === diaries.length - 1 ? '#475569' : '#cbd5e1', padding: '6px 10px', borderRadius: '6px', cursor: diaryIndex === diaries.length - 1 ? 'not-allowed' : 'pointer', display: 'flex', alignItems: 'center' }}
                  >
                    <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              )}
            </div>
            
            {activeDiary ? (
              <div style={{ animation: 'fadeIn 0.3s ease-in-out' }}>
                <div style={{ display: 'flex', gap: '16px', fontSize: '11.5px', color: '#94a3b8', marginBottom: '10px' }}>
                  <span>📅 일자: <strong>{activeDiary.diaryDate}</strong></span>
                  <span>📊 오늘 체결건수: <strong>{activeDiary.totalTrades} 건</strong></span>
                </div>
                <p style={{ margin: 0, fontSize: '13px', color: '#cbd5e1', lineHeight: '1.6', background: 'rgba(0,0,0,0.15)', padding: '16px', borderRadius: '10px', border: '1px solid rgba(255,255,255,0.03)' }}>
                  {activeDiary.feedbackContent}
                </p>
              </div>
            ) : (
              <div style={{ padding: '20px 12px', textAlign: 'center', fontSize: '13px', color: '#64748b' }}>
                아직 오늘 생성된 오답노트 일지가 존재하지 않습니다. 자동매매 체결이 완료된 후 저녁에 복기록이 생성됩니다.
              </div>
            )}
          </div>
        </div>
      )}

      {/* 2. Bot Settings Tab */}
      {activeTab === 'bots' && (
        <div className="bot-layout">
          {/* Bot Regist Form */}
          <div className="glass-card" style={{ height: 'fit-content' }}>
            <h3 style={{ margin: '0 0 16px 0', fontSize: '16px', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Plus className="w-5 h-5 text-indigo-400" />
              <span>{editingBot ? '🤖 자동매매 봇 정보 수정' : '신규 자동매매 봇 등록'}</span>
            </h3>

            {newBot.algorithmType !== 'REBALANCING' && !editingBot && (
              <div style={{ marginBottom: '20px', paddingBottom: '16px', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                <label style={{ display: 'block', fontSize: '11px', fontWeight: '600', color: '#818cf8', marginBottom: '6px' }}>
                  🔍 종목 빠른 검색 (이름/코드 자동완성)
                </label>
                <div className="dropdown-container">
                  <div style={{ display: 'flex', alignItems: 'center', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '8px', padding: '0 10px' }}>
                    <Search className="w-4 h-4 text-slate-500" />
                    <input 
                      type="text" 
                      value={searchQuery}
                      onChange={handleSearchChange}
                      placeholder="종목 이름 혹은 코드 입력 (예: 삼성)"
                      style={{ flex: 1, background: 'transparent', border: 'none', padding: '10px 8px', color: '#f8fafc', outline: 'none', fontSize: '13px' }}
                    />
                  </div>
                  {searchResults.length > 0 && (
                    <div className="dropdown-menu">
                      {searchResults.map((item) => (
                        <div 
                          key={item.symbol || item.code} 
                          onClick={() => selectSearchItem(item)}
                          className="dropdown-item"
                        >
                          <span style={{ fontWeight: 'bold' }}>{item.name}</span>
                          <span style={{ color: '#818cf8', fontSize: '11px' }}>{item.symbol || item.code}</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}

            <form onSubmit={handleSubmitBot} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              
              <div>
                <label style={{ display: 'block', fontSize: '11px', fontWeight: '600', color: '#94a3b8', marginBottom: '6px' }}>매매 알고리즘 선택</label>
                <select 
                  value={newBot.algorithmType} 
                  disabled={editingBot ? true : false}
                  onChange={(e) => {
                    setNewBot({ ...newBot, algorithmType: e.target.value });
                    setBacktestResult(null);
                  }}
                  style={{ width: '98%', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.08)', padding: '10px', borderRadius: '8px', color: '#f8fafc', outline: 'none', fontSize: '13px', opacity: editingBot ? 0.6 : 1 }}
                >
                  <option value="ACCUMULATION">적립식 매수 (Accumulation)</option>
                  <option value="RSI_ACCUMULATE">RSI 스마트 모으기 (RSI Accumulate)</option>
                  <option value="DROP_BUY">낙폭 과대 매수 (Drop Buy)</option>
                  <option value="MA_CROSS">이동평균선 골든/데드크로스 (MA Cross)</option>
                  <option value="REBALANCING">자산 배분 포트폴리오 리밸런싱 (Rebalancing)</option>
                </select>
              </div>

              {newBot.algorithmType !== 'REBALANCING' && (
                <>
                  <div>
                    <label style={{ display: 'block', fontSize: '11px', fontWeight: '600', color: '#94a3b8', marginBottom: '6px' }}>종목코드</label>
                    <input 
                      type="text" 
                      value={newBot.stockCode}
                      disabled={editingBot ? true : false}
                      onChange={(e) => setNewBot({ ...newBot, stockCode: e.target.value })}
                      placeholder="예: 005930"
                      style={{ width: '92%', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.08)', padding: '10px', borderRadius: '8px', color: '#f8fafc', outline: 'none', fontSize: '13px', opacity: editingBot ? 0.6 : 1 }}
                      required
                    />
                  </div>

                  <div>
                    <label style={{ display: 'block', fontSize: '11px', fontWeight: '600', color: '#94a3b8', marginBottom: '6px' }}>종목명</label>
                    <input 
                      type="text" 
                      value={newBot.stockName}
                      disabled={editingBot ? true : false}
                      onChange={(e) => setNewBot({ ...newBot, stockName: e.target.value })}
                      placeholder="예: 삼성전자"
                      style={{ width: '92%', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.08)', padding: '10px', borderRadius: '8px', color: '#f8fafc', outline: 'none', fontSize: '13px', opacity: editingBot ? 0.6 : 1 }}
                      required
                    />
                  </div>
                </>
              )}

              {newBot.algorithmType === 'REBALANCING' && (
                <div style={{ padding: '12px', background: 'rgba(99, 102, 241, 0.05)', borderRadius: '10px', border: '1px solid rgba(99, 102, 241, 0.15)' }}>
                  <div className="flex-row-between" style={{ marginBottom: '12px' }}>
                    <span style={{ fontSize: '12px', fontWeight: 'bold', color: '#818cf8' }}>⚖️ 포트폴리오 비중 설정</span>
                    <button 
                      type="button" 
                      onClick={addRebalanceTarget}
                      style={{ border: 'none', background: '#4f46e5', color: 'white', padding: '4px 8px', borderRadius: '4px', fontSize: '11px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '4px' }}
                    >
                      <Plus className="w-3.5 h-3.5" /> 추가
                    </button>
                  </div>
                  
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
                    {rebalanceTargets.map((item, idx) => (
                      <div key={idx} style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
                        <input 
                          type="text" 
                          value={item.code} 
                          onChange={(e) => updateRebalanceTarget(idx, 'code', e.target.value)} 
                          placeholder="코드 (005930)" 
                          style={{ flex: 2, background: '#0b0f19', border: '1px solid rgba(255,255,255,0.1)', padding: '6px', borderRadius: '6px', color: '#f8fafc', fontSize: '11px' }}
                          required
                        />
                        <input 
                          type="text" 
                          value={item.name} 
                          onChange={(e) => updateRebalanceTarget(idx, 'name', e.target.value)} 
                          placeholder="종목명" 
                          style={{ flex: 3, background: '#0b0f19', border: '1px solid rgba(255,255,255,0.1)', padding: '6px', borderRadius: '6px', color: '#f8fafc', fontSize: '11px' }}
                          required
                        />
                        <div style={{ display: 'flex', alignItems: 'center', gap: '2px', flex: 2 }}>
                          <input 
                            type="number" 
                            value={item.weight} 
                            onChange={(e) => updateRebalanceTarget(idx, 'weight', Number(e.target.value))} 
                            placeholder="비중" 
                            style={{ width: '80%', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.1)', padding: '6px', borderRadius: '6px', color: '#f8fafc', fontSize: '11px', textAlign: 'center' }}
                            required
                          />
                          <span style={{ fontSize: '11px', color: '#94a3b8' }}>%</span>
                        </div>
                        {rebalanceTargets.length > 1 && (
                          <button 
                            type="button" 
                            onClick={() => removeRebalanceTarget(idx)}
                            style={{ border: 'none', background: 'rgba(239,68,68,0.15)', color: '#fca5a5', padding: '6px', borderRadius: '6px', cursor: 'pointer' }}
                          >
                            <X className="w-3.5 h-3.5" />
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                  <div style={{ fontSize: '11px', display: 'flex', justifyContent: 'space-between', color: '#94a3b8' }}>
                    <span>* 비중의 합이 정확히 100%여야 합니다.</span>
                    <span style={{ fontWeight: 'bold', color: rebalanceTargets.reduce((acc, c) => acc + Number(c.weight), 0) === 100 ? '#34d399' : '#f87171' }}>
                      합계: {rebalanceTargets.reduce((acc, c) => acc + Number(c.weight), 0)}%
                    </span>
                  </div>
                </div>
              )}

              {newBot.algorithmType !== 'REBALANCING' && (
                <div>
                  <label style={{ display: 'block', fontSize: '11px', fontWeight: '600', color: '#94a3b8', marginBottom: '6px' }}>거래 기준 선택</label>
                  <div style={{ display: 'flex', gap: '16px' }}>
                    <label style={{ fontSize: '12px', display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer' }}>
                      <input 
                        type="radio" 
                        name="tradeUnit" 
                        value="amount" 
                        checked={tradeUnitType === 'amount'} 
                        onChange={() => setTradeUnitType('amount')}
                        style={{ cursor: 'pointer' }}
                      />
                      💵 1회 금액 기준 (원)
                    </label>
                    <label style={{ fontSize: '12px', display: 'flex', alignItems: 'center', gap: '6px', cursor: 'pointer' }}>
                      <input 
                        type="radio" 
                        name="tradeUnit" 
                        value="quantity" 
                        checked={tradeUnitType === 'quantity'} 
                        onChange={() => setTradeUnitType('quantity')}
                        style={{ cursor: 'pointer' }}
                      />
                      📊 1회 주수 기준 (주)
                    </label>
                  </div>
                </div>
              )}

              {newBot.algorithmType !== 'REBALANCING' && (
                <div style={{ padding: '12px', background: 'rgba(255,255,255,0.02)', borderRadius: '10px', border: '1px solid rgba(255,255,255,0.04)', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  <span style={{ fontSize: '11px', fontWeight: 'bold', color: '#818cf8', display: 'block' }}>
                    ⚙️ {newBot.algorithmType === 'ACCUMULATION' ? '적립식' : newBot.algorithmType === 'RSI_ACCUMULATE' ? 'RSI 스마트 모으기' : newBot.algorithmType === 'DROP_BUY' ? '낙폭과대' : '이평선돌파'} 전략 설정
                  </span>

                  {tradeUnitType === 'amount' ? (
                    <div>
                      <label style={{ display: 'block', fontSize: '10px', color: '#94a3b8', marginBottom: '4px' }}>💵 1회 약정 매수 금액 (원)</label>
                      <input 
                        type="number"
                        value={algoParams.amount}
                        onChange={(e) => setAlgoParams({ ...algoParams, amount: Number(e.target.value) })}
                        style={{ width: '88%', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.08)', padding: '8px', borderRadius: '6px', color: '#f8fafc', outline: 'none', fontSize: '12px' }}
                        required
                      />
                    </div>
                  ) : (
                    <div>
                      <label style={{ display: 'block', fontSize: '10px', color: '#94a3b8', marginBottom: '4px' }}>📊 1회 약정 매수 수량 (주)</label>
                      <input 
                        type="number"
                        value={algoParams.quantity}
                        onChange={(e) => setAlgoParams({ ...algoParams, quantity: Number(e.target.value) })}
                        style={{ width: '88%', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.08)', padding: '8px', borderRadius: '6px', color: '#f8fafc', outline: 'none', fontSize: '12px' }}
                        required
                      />
                      {/* 주수 기준 예상 소요 금액 실시간 표현 */}
                      {newBot.stockCode && (
                        <div style={{ marginTop: '6px', fontSize: '10.5px', color: '#a5b4fc', fontWeight: 'bold' }}>
                          💰 예상 1회 매수금액: {' '}
                          {prices.find(p => p.symbol === newBot.stockCode)?.lastPrice ? (
                            <span>₩ {(prices.find(p => p.symbol === newBot.stockCode).lastPrice * algoParams.quantity).toLocaleString()} 원</span>
                          ) : (
                            <span style={{ color: '#64748b' }}>시세 조회 중...</span>
                          )}
                        </div>
                      )}
                    </div>
                  )}

                  {newBot.algorithmType === 'RSI_ACCUMULATE' && (
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <div style={{ flex: 1 }}>
                        <label style={{ display: 'block', fontSize: '10px', color: '#94a3b8', marginBottom: '4px' }}>📈 RSI 계산 기간 (일)</label>
                        <input 
                          type="number"
                          value={algoParams.rsiPeriod || 14}
                          onChange={(e) => setAlgoParams({ ...algoParams, rsiPeriod: Number(e.target.value) })}
                          style={{ width: '80%', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.08)', padding: '8px', borderRadius: '6px', color: '#f8fafc', outline: 'none', fontSize: '12px' }}
                          required
                        />
                      </div>
                      <div style={{ flex: 1 }}>
                        <label style={{ display: 'block', fontSize: '10px', color: '#94a3b8', marginBottom: '4px' }}>📉 RSI 과매도 기준 (%)</label>
                        <input 
                          type="number"
                          value={algoParams.rsiThreshold || 30}
                          onChange={(e) => setAlgoParams({ ...algoParams, rsiThreshold: Number(e.target.value) })}
                          style={{ width: '80%', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.08)', padding: '8px', borderRadius: '6px', color: '#f8fafc', outline: 'none', fontSize: '12px' }}
                          required
                        />
                      </div>
                    </div>
                  )}

                  {(newBot.algorithmType === 'ACCUMULATION' || newBot.algorithmType === 'RSI_ACCUMULATE') && (
                    <div>
                      <label style={{ display: 'block', fontSize: '10px', color: '#94a3b8', marginBottom: '4px' }}>⏱ 매수 주기 (분 단위)</label>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                        <input 
                          type="number"
                          value={algoParams.intervalMinutes}
                          onChange={(e) => setAlgoParams({ ...algoParams, intervalMinutes: Number(e.target.value) })}
                          style={{ width: '88%', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.08)', padding: '8px', borderRadius: '6px', color: '#f8fafc', outline: 'none', fontSize: '12px' }}
                          required
                        />
                        
                        {/* 주기 조절용 ⏱ 프리셋 버튼 모음집 */}
                        <div style={{ display: 'flex', gap: '6px' }}>
                          <button
                            type="button"
                            onClick={() => setAlgoParams({ ...algoParams, intervalMinutes: 10 })}
                            style={{ border: '1px solid rgba(99, 102, 241, 0.25)', background: algoParams.intervalMinutes === 10 ? 'rgba(99, 102, 241, 0.3)' : 'rgba(99,102,241,0.08)', color: '#a5b4fc', padding: '4px 10px', borderRadius: '6px', fontSize: '10.5px', cursor: 'pointer', transition: 'all 0.2s' }}
                          >
                            ⏱ 10분 테스트
                          </button>
                          <button
                            type="button"
                            onClick={() => setAlgoParams({ ...algoParams, intervalMinutes: 60 })}
                            style={{ border: '1px solid rgba(99, 102, 241, 0.25)', background: algoParams.intervalMinutes === 60 ? 'rgba(99, 102, 241, 0.3)' : 'rgba(99,102,241,0.08)', color: '#a5b4fc', padding: '4px 10px', borderRadius: '6px', fontSize: '10.5px', cursor: 'pointer', transition: 'all 0.2s' }}
                          >
                            ⏱ 1시간 집중
                          </button>
                          <button
                            type="button"
                            onClick={() => setAlgoParams({ ...algoParams, intervalMinutes: 1440 })}
                            style={{ border: '1px solid rgba(99, 102, 241, 0.25)', background: algoParams.intervalMinutes === 1440 ? 'rgba(99, 102, 241, 0.3)' : 'rgba(99,102,241,0.08)', color: '#a5b4fc', padding: '4px 10px', borderRadius: '6px', fontSize: '10.5px', cursor: 'pointer', transition: 'all 0.2s' }}
                          >
                            ⏱ 1일 적립
                          </button>
                        </div>
                      </div>
                    </div>
                  )}

                  {newBot.algorithmType === 'DROP_BUY' && (
                    <>
                      <div>
                        <label style={{ display: 'block', fontSize: '10px', color: '#94a3b8', marginBottom: '4px' }}>📉 기준 하락률 (%)</label>
                        <input 
                          type="number"
                          step="0.1"
                          value={algoParams.dropPercent}
                          onChange={(e) => setAlgoParams({ ...algoParams, dropPercent: Number(e.target.value) })}
                          style={{ width: '88%', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.08)', padding: '8px', borderRadius: '6px', color: '#f8fafc', outline: 'none', fontSize: '12px' }}
                          required
                        />
                      </div>
                      <div>
                        <label style={{ display: 'block', fontSize: '10px', color: '#94a3b8', marginBottom: '4px' }}>📅 최고가 감시 범위 (일)</label>
                        <input 
                          type="number"
                          value={algoParams.lookbackDays}
                          onChange={(e) => setAlgoParams({ ...algoParams, lookbackDays: Number(e.target.value) })}
                          style={{ width: '88%', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.08)', padding: '8px', borderRadius: '6px', color: '#f8fafc', outline: 'none', fontSize: '12px' }}
                          required
                        />
                      </div>
                      <div>
                        <label style={{ display: 'block', fontSize: '10px', color: '#94a3b8', marginBottom: '4px' }}>🎯 목표 수익률 (%) (익절 목표)</label>
                        <input 
                          type="number"
                          step="0.1"
                          value={algoParams.targetProfitPercent}
                          onChange={(e) => setAlgoParams({ ...algoParams, targetProfitPercent: Number(e.target.value) })}
                          style={{ width: '88%', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.08)', padding: '8px', borderRadius: '6px', color: '#f8fafc', outline: 'none', fontSize: '12px' }}
                          required
                        />
                      </div>
                    </>
                  )}

                  {newBot.algorithmType === 'MA_CROSS' && (
                    <>
                      <div>
                        <label style={{ display: 'block', fontSize: '10px', color: '#94a3b8', marginBottom: '4px' }}>⚡ 단기 이동평균일 (일)</label>
                        <input 
                          type="number"
                          value={algoParams.shortPeriod}
                          onChange={(e) => setAlgoParams({ ...algoParams, shortPeriod: Number(e.target.value) })}
                          style={{ width: '88%', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.08)', padding: '8px', borderRadius: '6px', color: '#f8fafc', outline: 'none', fontSize: '12px' }}
                          required
                        />
                      </div>
                      <div>
                        <label style={{ display: 'block', fontSize: '10px', color: '#94a3b8', marginBottom: '4px' }}>🐢 장기 이동평균일 (일)</label>
                        <input 
                          type="number"
                          value={algoParams.longPeriod}
                          onChange={(e) => setAlgoParams({ ...algoParams, longPeriod: Number(e.target.value) })}
                          style={{ width: '88%', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.08)', padding: '8px', borderRadius: '6px', color: '#f8fafc', outline: 'none', fontSize: '12px' }}
                          required
                        />
                      </div>
                    </>
                  )}
                </div>
              )}

              {newBot.algorithmType !== 'REBALANCING' && (
                <div style={{ padding: '12px', background: 'rgba(239, 68, 68, 0.03)', borderRadius: '10px', border: '1px solid rgba(239, 68, 68, 0.12)', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontSize: '11px', fontWeight: 'bold', color: '#f87171' }}>🚨 지능형 위험제어 (손절/익절)</span>
                    <input 
                      type="checkbox" 
                      checked={riskParams.useRiskControl} 
                      onChange={(e) => setRiskParams({ ...riskParams, useRiskControl: e.target.checked })} 
                      style={{ cursor: 'pointer' }}
                    />
                  </div>
                  {riskParams.useRiskControl && (
                    <div style={{ display: 'flex', gap: '10px' }}>
                      <div style={{ flex: 1 }}>
                        <label style={{ display: 'block', fontSize: '9px', color: '#ef4444', marginBottom: '4px' }}>손절 기준한도 (%)</label>
                        <input 
                          type="number" 
                          step="0.1" 
                          value={riskParams.stopLossPercent} 
                          onChange={(e) => setRiskParams({ ...riskParams, stopLossPercent: Number(e.target.value) })} 
                          style={{ width: '82%', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.1)', padding: '6px', borderRadius: '6px', color: '#f8fafc', fontSize: '11px', textAlign: 'center' }} 
                        />
                      </div>
                      <div style={{ flex: 1 }}>
                        <label style={{ display: 'block', fontSize: '9px', color: '#10b981', marginBottom: '4px' }}>익절 기준한도 (%)</label>
                        <input 
                          type="number" 
                          step="0.1" 
                          value={riskParams.takeProfitPercent} 
                          onChange={(e) => setRiskParams({ ...riskParams, takeProfitPercent: Number(e.target.value) })} 
                          style={{ width: '82%', background: '#0b0f19', border: '1px solid rgba(255,255,255,0.1)', padding: '6px', borderRadius: '6px', color: '#f8fafc', fontSize: '11px', textAlign: 'center' }} 
                        />
                      </div>
                    </div>
                  )}
                </div>
              )}

              {newBot.algorithmType !== 'REBALANCING' && (
                <button 
                  type="button"
                  onClick={handleRunBacktest}
                  disabled={backtesting}
                  style={{ width: '100%', border: '1px solid #6366f1', background: 'rgba(99, 102, 241, 0.05)', color: '#a5b4fc', fontWeight: '600', padding: '10px 0', borderRadius: '8px', cursor: 'pointer', transition: 'opacity 0.2s', fontSize: '12px' }}
                >
                  {backtesting ? '시뮬레이션 분석 중...' : '📊 과거 15일 백테스팅 시뮬레이션'}
                </button>
              )}

              {backtestResult && (
                <div style={{ padding: '14px', background: '#0f172a', borderRadius: '10px', border: '1px solid rgba(99,102,241,0.2)' }}>
                  <h4 style={{ margin: '0 0 10px 0', fontSize: '12px', fontWeight: 'bold', color: '#818cf8' }}>
                    📊 과거 15일 모의 투자 결과 리포트
                  </h4>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', fontSize: '11px', color: '#94a3b8', marginBottom: '12px' }}>
                    <div>
                      <span>최종 가상자산:</span>{' '}
                      <strong style={{ color: '#f1f5f9' }}>₩ {backtestResult.finalAsset.toLocaleString()}</strong>
                    </div>
                    <div>
                      <span>시뮬레이션 수익률:</span>{' '}
                      <strong style={{ color: backtestResult.profitRate >= 0 ? '#ef4444' : '#3b82f6' }}>
                        {backtestResult.profitRate >= 0 ? '+' : ''}{backtestResult.profitRate}%
                      </strong>
                    </div>
                    <div>
                      <span>체결 거래건수:</span>{' '}
                      <strong style={{ color: '#f1f5f9' }}>{backtestResult.totalTrades} 회</strong>
                    </div>
                    <div>
                      <span>익절 성공률:</span>{' '}
                      <strong style={{ color: '#10b981' }}>
                        {backtestResult.totalTrades > 0 ? ((backtestResult.winTrades / backtestResult.totalTrades) * 100).toFixed(0) : 0}%
                      </strong>
                    </div>
                  </div>

                  <div style={{ height: '110px', width: '100%' }}>
                    <ResponsiveContainer width="100%" height="100%">
                      <AreaChart data={backtestResult.history}>
                        <defs>
                          <linearGradient id="backtestGrad" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="#6366f1" stopOpacity={0.3}/>
                            <stop offset="95%" stopColor="#6366f1" stopOpacity={0}/>
                          </linearGradient>
                        </defs>
                        <CartesianGrid strokeDasharray="2 2" stroke="#1e293b" />
                        <XAxis dataKey="date" hide />
                        <YAxis domain={['auto', 'auto']} hide />
                        <Tooltip 
                          contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', fontSize: '10px' }}
                          formatter={(val) => [`₩${val.toLocaleString()}`, '가상 자산']}
                        />
                        <Area type="monotone" dataKey="totalAsset" stroke="#818cf8" strokeWidth={2} fillOpacity={1} fill="url(#backtestGrad)" />
                      </AreaChart>
                    </ResponsiveContainer>
                  </div>
                </div>
              )}

              <div style={{ display: 'flex', gap: '8px' }}>
                <button 
                  type="submit" 
                  className="bg-gradient-premium"
                  style={{ flex: 2, border: 'none', color: 'white', fontWeight: '600', padding: '10px 0', borderRadius: '8px', cursor: 'pointer', transition: 'opacity 0.2s' }}
                >
                  {editingBot ? '봇 수정 완료' : '봇 추가하기'}
                </button>
                {editingBot && (
                  <button 
                    type="button" 
                    onClick={() => {
                      setEditingBot(null);
                      setNewBot({ stockCode: '', stockName: '', algorithmType: 'ACCUMULATION', active: false });
                    }}
                    style={{ flex: 1, border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(255,255,255,0.05)', color: '#94a3b8', fontWeight: '600', padding: '10px 0', borderRadius: '8px', cursor: 'pointer' }}
                  >
                    취소
                  </button>
                )}
              </div>
            </form>
          </div>

          {/* Active Bots List */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold' }}>감시 중인 봇 목록 ({bots.length})</h3>
            
            <div className="bot-grid">
              {bots.map((bot) => (
                <div key={bot.id} className="glass-card" style={{ display: 'flex', flexDirection: 'column', justifycontent: 'space-between', padding: '20px' }}>
                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '12px' }}>
                      <div>
                        <h4 style={{ margin: 0, fontSize: '15px', fontWeight: 'bold', color: '#f1f5f9' }}>{bot.stockName}</h4>
                        <span style={{ fontSize: '11px', color: '#64748b' }}>{bot.stockCode}</span>
                      </div>
                      <span 
                        style={{
                          padding: '2px 8px',
                          borderRadius: '10px',
                          fontSize: '10px',
                          fontWeight: 'bold',
                          background: bot.active ? 'rgba(16,185,129,0.1)' : 'rgba(255,255,255,0.05)',
                          color: bot.active ? '#34d399' : '#64748b',
                          border: bot.active ? '1px solid rgba(16,185,129,0.2)' : '1px solid rgba(255,255,255,0.08)'
                        }}
                      >
                        {bot.active ? '감시 중' : '정지됨'}
                      </span>
                    </div>

                    <div style={{ fontSize: '12px', marginBottom: '16px' }}>
                      <p style={{ margin: '0 0 6px 0', color: '#94a3b8' }}>
                        <span style={{ fontWeight: '600' }}>알고리즘:</span> <span style={{ color: '#818cf8', fontFamily: 'monospace' }}>{bot.algorithmType}</span>
                      </p>
                      
                      {(bot.stopLossPercent || bot.takeProfitPercent) && (
                        <div style={{ display: 'flex', gap: '8px', marginBottom: '8px', fontSize: '10px' }}>
                          {bot.stopLossPercent && (
                            <span style={{ background: 'rgba(239,68,68,0.1)', color: '#fca5a5', padding: '2px 6px', borderRadius: '4px', border: '1px solid rgba(239,68,68,0.2)' }}>
                              손절 한도: -{bot.stopLossPercent}%
                            </span>
                          )}
                          {bot.takeProfitPercent && (
                            <span style={{ background: 'rgba(16,185,129,0.1)', color: '#34d399', padding: '2px 6px', borderRadius: '4px', border: '1px solid rgba(16,185,129,0.2)' }}>
                              익절 한도: +{bot.takeProfitPercent}%
                            </span>
                          )}
                        </div>
                      )}

                      <div style={{ background: '#0b0f19', padding: '10px', borderRadius: '8px', border: '1px solid rgba(255,255,255,0.05)', fontFamily: 'monospace', fontSize: '10px', color: '#a5b4fc', whiteSpace: 'pre-wrap', maxHeight: '110px', overflowY: 'auto' }}>
                        {bot.parameters}
                      </div>

                      {bot.active && bot.statusMessage && (
                        <div style={{
                          marginTop: '12px',
                          padding: '8px 10px',
                          background: 'rgba(99, 102, 241, 0.04)',
                          borderRadius: '8px',
                          border: '1px solid rgba(99, 102, 241, 0.12)',
                          fontSize: '11px',
                          color: '#cbd5e1',
                          display: 'flex',
                          alignItems: 'center',
                          gap: '6px'
                        }}>
                          <span style={{ display: 'inline-block', width: '6px', height: '6px', borderRadius: '50%', backgroundColor: '#10b981', animation: 'ping 1.5s infinite' }}></span>
                          <span style={{ color: '#a5b4fc', fontWeight: 'bold' }}>Live:</span>
                          <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={bot.statusMessage}>{bot.statusMessage}</span>
                        </div>
                      )}
                    </div>
                  </div>

                  <div style={{ display: 'flex', gap: '8px', paddingTop: '10px', borderTop: '1px solid rgba(255,255,255,0.06)' }}>
                    <button 
                      onClick={() => handleToggleBot(bot.id)}
                      style={{
                        flex: 2,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: '6px',
                        padding: '6px 0',
                        borderRadius: '6px',
                        border: 'none',
                        fontSize: '11px',
                        fontWeight: 'bold',
                        cursor: 'pointer',
                        background: bot.active ? 'rgba(245,158,11,0.15)' : 'rgba(16,185,129,0.15)',
                        color: bot.active ? '#fbbf24' : '#34d399'
                      }}
                    >
                      {bot.active ? (
                        <>
                          <Square className="w-3.5 h-3.5" />
                          <span>정지하기</span>
                        </>
                      ) : (
                        <>
                          <Play className="w-3.5 h-3.5" />
                          <span>감시시작</span>
                        </>
                      )}
                    </button>

                    <button
                      onClick={() => handleStartEditBot(bot)}
                      style={{
                        flex: 1,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: '4px',
                        padding: '6px 0',
                        borderRadius: '6px',
                        border: '1px solid rgba(99,102,241,0.3)',
                        background: 'rgba(99,102,241,0.08)',
                        color: '#a5b4fc',
                        fontSize: '11px',
                        fontWeight: 'bold',
                        cursor: 'pointer'
                      }}
                    >
                      <Edit className="w-3 h-3" />
                      <span>수정</span>
                    </button>

                    <button 
                      onClick={() => handleDeleteBot(bot.id)}
                      style={{ padding: '6px 8px', borderRadius: '6px', border: 'none', background: 'rgba(239,68,68,0.15)', color: '#fca5a5', cursor: 'pointer', display: 'flex', alignItems: 'center' }}
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* 3. Trading History Tab */}
      {activeTab === 'history' && (
        <div className="glass-card" style={{ padding: '24px', overflow: 'hidden' }}>
          <div className="flex-row-between" style={{ marginBottom: '24px' }}>
            <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <ListFilter className="w-5 h-5 text-indigo-400" />
              <span>전체 매매 거래 내역 ({orders.length} 건)</span>
            </h3>
          </div>
          
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px', color: '#cbd5e1' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.08)', color: '#94a3b8', fontWeight: '600', background: 'rgba(15,23,42,0.3)' }}>
                  <th style={{ padding: '12px' }}>시간</th>
                  <th style={{ padding: '12px' }}>종목</th>
                  <th style={{ padding: '12px' }}>구분</th>
                  <th style={{ padding: '12px', textAlign: 'right' }}>단가</th>
                  <th style={{ padding: '12px', textAlign: 'right' }}>수량</th>
                  <th style={{ padding: '12px', textAlign: 'right' }}>합계금액</th>
                  <th style={{ padding: '12px' }}>상태</th>
                  <th style={{ padding: '12px', textAlign: 'center' }}>모드</th>
                </tr>
              </thead>
              <tbody>
                {orders.map((o) => (
                  <tr key={o.id} style={{ borderBottom: '1px solid rgba(255,255,255,0.05)', transition: 'background 0.2s' }} className="table-row-hover">
                    <td style={{ padding: '12px', color: '#64748b' }}>
                      {new Date(o.orderTime).toLocaleString()}
                    </td>
                    <td style={{ padding: '12px', fontWeight: 'bold', color: '#f1f5f9' }}>
                      {o.stockName} <span style={{ fontSize: '11px', color: '#64748b', fontWeight: 'normal' }}>({o.stockCode})</span>
                    </td>
                    <td style={{ padding: '12px' }}>
                      <span 
                        style={{
                          padding: '2px 8px',
                          borderRadius: '10px',
                          fontSize: '11px',
                          fontWeight: 'bold',
                          background: o.orderType === 'BUY' ? 'rgba(59,130,246,0.1)' : 'rgba(239,68,68,0.1)',
                          color: o.orderType === 'BUY' ? '#93c5fd' : '#fca5a5'
                        }}
                      >
                        {o.orderType === 'BUY' ? '매수' : '매도'}
                      </span>
                    </td>
                    <td style={{ padding: '12px', textAlign: 'right', fontFamily: 'monospace' }}>
                      ₩ {o.price.toLocaleString()}
                    </td>
                    <td style={{ padding: '12px', textAlign: 'right', fontFamily: 'monospace' }}>
                      {o.quantity} 주
                    </td>
                    <td style={{ padding: '12px', textAlign: 'right', fontFamily: 'monospace', fontWeight: '600', color: '#f1f5f9' }}>
                      ₩ {(o.price * o.quantity).toLocaleString()}
                    </td>
                    <td style={{ padding: '12px' }}>
                      <span style={{ padding: '2px 6px', borderRadius: '4px', fontSize: '11px', fontWeight: '600', background: o.status === 'FILLED' ? 'rgba(16,185,129,0.1)' : 'rgba(245,158,11,0.1)', color: o.status === 'FILLED' ? '#34d399' : '#fbbf24' }}>
                        {o.status === 'FILLED' ? '체결완료' : o.status}
                      </span>
                    </td>
                    <td style={{ padding: '12px', textAlign: 'center' }}>
                      <span style={{ padding: '2px 6px', borderRadius: '4px', fontSize: '9px', fontWeight: 'bold', background: o.tradingMode === 'MOCK' ? 'rgba(255,255,255,0.05)' : '#4f46e5', color: o.tradingMode === 'MOCK' ? '#94a3b8' : '#ffffff', border: o.tradingMode === 'MOCK' ? '1px solid rgba(255,255,255,0.08)' : 'none' }}>
                        {o.tradingMode}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* 4. AI Q&A Interactive Chat Tab */}
      {activeTab === 'ai-chat' && (
        <div className="space-y-6" style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          
          {/* Top Banner & Quick Recommendation Questions */}
          <div className="glass-card" style={{ padding: '20px 24px', borderLeft: '4px solid #6366f1', background: 'rgba(99,102,241,0.02)' }}>
            <div className="flex-row-between" style={{ marginBottom: '12px' }}>
              <h3 style={{ margin: 0, fontSize: '16px', fontWeight: 'bold', color: '#f1f5f9', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Cpu className="w-5 h-5 text-indigo-400" />
                <span>💬 Gemini & Llama 3.1 융합 AI 퀀트 주식 상담소</span>
              </h3>
              <span style={{ fontSize: '11px', background: 'rgba(99,102,241,0.15)', color: '#a5b4fc', padding: '3px 10px', borderRadius: '12px', border: '1px solid rgba(99,102,241,0.3)', fontWeight: 'bold' }}>
                실시간 시세 데이터 연동 중
              </span>
            </div>
            <p style={{ margin: '0 0 14px 0', fontSize: '12px', color: '#94a3b8', lineHeight: '1.5' }}>
              주주님의 투자 성향과 궁금한 점을 자연스럽게 물어보세요! AI가 실시간 퀀트 시세 및 뉴스를 종합 분석하여 맞춤 종목을 추천해 드립니다. 
              추천 카드 하단의 <strong style={{ color: '#38bdf8' }}>돋보기 🔍</strong> 버튼을 클릭하면 메인 차트로 바로 이동합니다.
            </p>

            {/* Quick Suggestion Chips */}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
              <button
                onClick={() => handleSendChatMessage('지금 상승 확률이 가장 높은 종목 추천해줘')}
                disabled={chatLoading}
                style={{ border: '1px solid rgba(99,102,241,0.25)', background: 'rgba(99,102,241,0.08)', color: '#a5b4fc', padding: '6px 12px', borderRadius: '20px', fontSize: '11.5px', fontWeight: '500', cursor: 'pointer', transition: 'all 0.2s' }}
                className="hover:bg-indigo-600/20"
              >
                🔥 "지금 상승 확률이 가장 높은 종목 추천해줘"
              </button>
              <button
                onClick={() => handleSendChatMessage('안전하게 분할 매수하기 좋은 우량주 알려줘')}
                disabled={chatLoading}
                style={{ border: '1px solid rgba(16,185,129,0.25)', background: 'rgba(16,185,129,0.08)', color: '#34d399', padding: '6px 12px', borderRadius: '20px', fontSize: '11.5px', fontWeight: '500', cursor: 'pointer', transition: 'all 0.2s' }}
              >
                🛡️ "안전하게 분할 매수하기 좋은 우량주 알려줘"
              </button>
              <button
                onClick={() => handleSendChatMessage('삼성전자 및 SK하이닉스 단기 기술적 차트 진단해줘')}
                disabled={chatLoading}
                style={{ border: '1px solid rgba(245,158,11,0.25)', background: 'rgba(245,158,11,0.08)', color: '#fbbf24', padding: '6px 12px', borderRadius: '20px', fontSize: '11.5px', fontWeight: '500', cursor: 'pointer', transition: 'all 0.2s' }}
              >
                📊 "삼성전자 및 SK하이닉스 단기 차트 진단해줘"
              </button>
            </div>
          </div>

          {/* Main Chat Dialog Box */}
          <div className="glass-card" style={{ padding: '20px', display: 'flex', flexDirection: 'column', height: '520px' }}>
            
            {/* Chat Messages List */}
            <div style={{ flex: 1, overflowY: 'auto', paddingRight: '6px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
              {chatMessages.map((msg, index) => (
                <div 
                  key={index}
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: msg.sender === 'user' ? 'flex-end' : 'flex-start'
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px' }}>
                    {msg.sender === 'ai' ? (
                      <>
                        <Cpu className="w-3.5 h-3.5 text-indigo-400" />
                        <span style={{ fontSize: '11px', fontWeight: 'bold', color: '#a5b4fc' }}>AI 주식 컨설턴트</span>
                      </>
                    ) : (
                      <span style={{ fontSize: '11px', fontWeight: 'bold', color: '#94a3b8' }}>주주님</span>
                    )}
                  </div>

                  {/* Message Content Bubble */}
                  <div style={{
                    maxWidth: '85%',
                    padding: '12px 16px',
                    borderRadius: msg.sender === 'user' ? '16px 16px 2px 16px' : '16px 16px 16px 2px',
                    background: msg.sender === 'user' ? '#312e81' : 'rgba(15,23,42,0.6)',
                    color: '#f8fafc',
                    fontSize: '13px',
                    lineHeight: '1.6',
                    border: msg.sender === 'user' ? '1px solid rgba(99,102,241,0.4)' : '1px solid rgba(255,255,255,0.08)',
                    whiteSpace: 'pre-wrap'
                  }}>
                    {msg.text}
                  </div>

                  {/* Recommended Stock Cards Container inside AI response */}
                  {msg.sender === 'ai' && msg.recommendedStocks && msg.recommendedStocks.length > 0 && (
                    <div style={{ marginTop: '12px', width: '100%', maxWidth: '85%' }}>
                      <div style={{ fontSize: '11px', color: '#94a3b8', fontWeight: 'bold', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                        <span>💡 AI 분석 추천 종목 미니 카드:</span>
                        <span style={{ fontSize: '10px', color: '#64748b' }}>(버튼을 클릭하면 기능이 연동됩니다)</span>
                      </div>
                      
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: '10px' }}>
                        {msg.recommendedStocks.map((st, sIdx) => (
                          <div 
                            key={sIdx}
                            style={{
                              padding: '12px',
                              background: 'rgba(30, 41, 59, 0.7)',
                              borderRadius: '10px',
                              border: '1px solid rgba(99,102,241,0.3)',
                              display: 'flex',
                              flexDirection: 'column',
                              justify: 'space-between',
                              gap: '8px'
                            }}
                          >
                            <div>
                              <div className="flex-row-between" style={{ marginBottom: '4px' }}>
                                <span style={{ fontWeight: 'bold', fontSize: '13.5px', color: '#ffffff' }}>{st.name}</span>
                                <span style={{ fontSize: '10px', color: '#818cf8', fontFamily: 'monospace' }}>{st.code}</span>
                              </div>
                              <div style={{ fontSize: '11.5px', color: '#cbd5e1', marginBottom: '4px' }}>
                                ₩ {st.price ? st.price.toLocaleString() : '---'}
                              </div>
                              <p style={{ margin: 0, fontSize: '10.5px', color: '#94a3b8', lineHeight: '1.4' }}>
                                {st.reason}
                              </p>
                            </div>

                            {/* Action Buttons: 🔍 차트보기 & 🤖 봇등록 */}
                            <div style={{ display: 'flex', gap: '6px', paddingTop: '6px', borderTop: '1px dashed rgba(255,255,255,0.06)' }}>
                              <button
                                onClick={() => handleViewStockFromChat(st.code, st.name)}
                                style={{
                                  flex: 1,
                                  border: 'none',
                                  background: 'rgba(56, 189, 248, 0.15)',
                                  color: '#38bdf8',
                                  padding: '5px 8px',
                                  borderRadius: '6px',
                                  fontSize: '10.5px',
                                  fontWeight: 'bold',
                                  cursor: 'pointer',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  gap: '4px'
                                }}
                              >
                                <Search className="w-3 h-3" />
                                🔍 차트 보기
                              </button>
                              <button
                                onClick={() => handleQuickBotRegister(st.code, st.name)}
                                style={{
                                  flex: 1,
                                  border: 'none',
                                  background: 'rgba(16, 185, 129, 0.15)',
                                  color: '#34d399',
                                  padding: '5px 8px',
                                  borderRadius: '6px',
                                  fontSize: '10.5px',
                                  fontWeight: 'bold',
                                  cursor: 'pointer',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  gap: '4px'
                                }}
                              >
                                <Plus className="w-3 h-3" />
                                🤖 봇 등록
                              </button>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                </div>
              ))}

              {/* Chat Loading Spinner Indicator */}
              {chatLoading && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '12px', background: 'rgba(99,102,241,0.05)', borderRadius: '12px', width: 'fit-content', border: '1px dashed rgba(99,102,241,0.2)' }}>
                  <Cpu className="w-4 h-4 text-indigo-400 animate-spin" />
                  <span style={{ fontSize: '12px', color: '#a5b4fc', fontWeight: 'bold' }}>AI 컨설턴트가 퀀트 시세를 분석하여 답변을 생성하는 중...</span>
                </div>
              )}
            </div>

            {/* Input Bar Section */}
            <form 
              onSubmit={(e) => {
                e.preventDefault();
                handleSendChatMessage();
              }}
              style={{ marginTop: '16px', display: 'flex', gap: '10px', pt: '12px', borderTop: '1px solid rgba(255,255,255,0.06)' }}
            >
              <input
                type="text"
                value={chatInput}
                onChange={(e) => setChatInput(e.target.value)}
                placeholder="주식 추천, 매수 타점, 시장 분석 등 궁금한 점을 물어보세요... (예: 상승 확률 높은 종목 알려줘)"
                style={{
                  flex: 1,
                  background: '#0b0f19',
                  border: '1px solid rgba(255,255,255,0.1)',
                  borderRadius: '10px',
                  padding: '12px 14px',
                  color: '#f8fafc',
                  fontSize: '13px',
                  outline: 'none'
                }}
                disabled={chatLoading}
              />
              <button
                type="submit"
                disabled={chatLoading || !chatInput.trim()}
                style={{
                  border: 'none',
                  background: chatInput.trim() ? '#4f46e5' : '#334155',
                  color: 'white',
                  padding: '0 20px',
                  borderRadius: '10px',
                  fontWeight: 'bold',
                  fontSize: '13px',
                  cursor: chatInput.trim() && !chatLoading ? 'pointer' : 'not-allowed',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  transition: 'all 0.2s'
                }}
              >
                <Send className="w-4 h-4" />
                질문하기
              </button>
            </form>

          </div>
        </div>
      )}
    </div>
  );
}
