# toss-ai-trader (토스 AI 자동매매 플랫폼)

> **토스증권 OpenAPI와 AI 멀티에이전트(Gemini & Llama) 투자위원회의 실시간 분석 기반 주식 자동투자 웹 플랫폼**

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue.svg)](https://reactjs.org/)
[![Vite](https://img.shields.io/badge/Vite-5-646CFF.svg)](https://vitejs.dev/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

---

## ⚠️ 비공식 프로젝트 안내 및 투자 유의사항

* **비공식 프로젝트:** 본 프로젝트는 개인이 연구 및 포트폴리오 목적으로 제작한 **비공식(Unofficial) 오픈소스 프로젝트**이며, 비바리퍼블리카(토스) 및 토스증권의 공식 서비스가 아닙니다.
* **투자 책임 면책:** 본 프로그램에서 제공하는 AI 분석 및 자동 매매 기능은 참고용이며, 실제 투자에 따른 모든 손익과 책임은 사용자 본인에게 있습니다.

---

## ✨ 핵심 기능 (Key Features)

1. **토스증권 OpenAPI 실시간 연동**
   * 실시간 계좌 잔고 조회, 주문 접수(매수/매도), 보유 종목 평가손익 연동
   * 모의 트레이딩(Mock Mode) 및 실전 트레이딩(Real Mode) 지원
2. **AI 투자위원회 (AI Multi-Agent Committee)**
   * **Google Gemini 1.5 Flash:** 실시간 차트 데이터(일봉/이평선/거래량) 및 최신 뉴스 헤드라인을 융합 분석하여 매매 시그널(RECOMMEND/HOLD/WARNING) 및 적정 진입가 산출
   * **Meta Llama 3.1 70B (Groq):** 거시 경제 및 독립적 차트 분석을 통한 듀얼 검증 및 합의(Consensus) 도출
3. **실시간 뉴스 및 시장 감성(Sentiment) 분석**
   * 네이버/구글 금융 뉴스 크롤링 기반 개미 투자자 여론 및 시장 호악재 분석
4. **리스크 관리 시스템 (Circuit Breaker)**
   * 변동성 급증 및 급락 구간 감지 시 자동 매매 중단 및 자산 보호(손절/익절 자동화)
5. **실시간 알림 센터**
   * 텔레그램(Telegram) 체결 알림 및 Gmail 일일 자산 리포트 발송

---

## 🛠️ 기술 스택 (Tech Stack)

### Backend
* **Language / Framework:** Java 17+, Spring Boot 3.x
* **Data Access:** Spring Data JPA, SQLite
* **Web Client:** Spring WebFlux (`WebClient`)
* **AI Integration:** Google Gemini API, Groq (Llama 3.1)

### Frontend
* **Library / Build:** React 18, Vite
* **Styling:** Tailwind CSS, Lucide Icons
* **Charts:** Recharts

---

## 🚀 빠른 시작 가이드 (Getting Started)

### 1. 레포지토리 클론
```bash
git clone https://github.com/jus0411/toss-ai-trader.git
cd toss-ai-trader
```

### 2. 환경 변수 설정
프로젝트 루트의 `.env.example`을 복사하여 `.env` 파일을 생성하고 본인의 API 키를 입력합니다:
```bash
cp .env.example .env
```

`.env` 파일 내용 편집:
```properties
# 'mock' (가상 모의투자 권장) 또는 'real' (실전 매매)
TRADING_MODE=mock

# 토스 OpenAPI 인증키 (토스 개발자 센터 발급)
TOSS_CLIENT_ID=your_toss_client_id_here
TOSS_CLIENT_SECRET=your_toss_client_secret_here

# Google Gemini API Key (무료 발급: Google AI Studio)
GEMINI_API_KEY=your_gemini_api_key_here

# Groq API Key (Llama 3.1 듀얼 위원회용, 선택사항)
GROQ_API_KEY=your_groq_api_key_here

# 알림 설정 (선택사항)
TELEGRAM_BOT_TOKEN=
TELEGRAM_CHAT_ID=
GMAIL_USERNAME=
GMAIL_PASSWORD=
REPORT_EMAIL_RECIPIENT=
```

### 3. 백엔드 실행 (Spring Boot)
```bash
cd backend
./mvnw spring-boot:run
```
> 백엔드 포트: `http://localhost:8081`

### 4. 프론트엔드 실행 (React)
```bash
cd frontend
npm install
npm run dev
```
> 프론트엔드 로컬 접속: `http://localhost:5173`

---

## 📄 라이선스 (License)

본 프로젝트는 [MIT License](LICENSE)를 따릅니다.
