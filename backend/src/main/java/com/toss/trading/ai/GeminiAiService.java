package com.toss.trading.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toss.trading.toss.dto.TossDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiAiService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final NewsSearchService newsSearchService;

    @Value("${trading.gemini.api-key:}")
    private String geminiApiKey;

    public GeminiAiService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, NewsSearchService newsSearchService) {
        this.webClient = webClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.objectMapper = objectMapper;
        this.newsSearchService = newsSearchService;
    }

    /**
     * 캔들 차트 데이터와 실시간 뉴스를 기반으로 Gemini AI 융합 투자 보고서를 획득합니다 (예수금 비중 수량 연산 탑재).
     */
    public Map<String, Object> analyzeStock(String symbol, String name, List<TossDto.CandleDto> candles, double cash) {
        // 실시간 뉴스 수집
        List<Map<String, String>> newsFeed = newsSearchService.searchStockNews(name);

        // API 키가 없거나 디폴트 문구인 경우 더미 시뮬레이션 데이터를 즉시 반환
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty() || "your_gemini_key_here".equals(geminiApiKey)) {
            return generateMockReport(symbol, name, candles, newsFeed, cash);
        }

        try {
            BigDecimal currentPrice = candles.isEmpty() ? BigDecimal.ZERO : candles.get(0).getClose();
            
            // 1. 프롬프트 작성
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append(String.format("너는 전문 퀀트 투자 분석가이자 재무 기자이다. 다음은 주식 %s (%s)의 최근 15일 간의 일봉 데이터(종가 및 거래량), 최근 구글 뉴스 기사 목록, 그리고 현재 사용자가 쥐고 있는 가용 가상 예수금(현찰)이다.\n\n", name, symbol));
            
            promptBuilder.append("### 최근 캔들 시세:\n");
            for (int i = Math.min(candles.size(), 15) - 1; i >= 0; i--) {
                TossDto.CandleDto c = candles.get(i);
                promptBuilder.append(String.format("- 날짜: %s, 종가: ₩%,.0f원, 거래량: %,.0f주\n", 
                        c.getTimestamp().split("T")[0], c.getClose().doubleValue(), c.getVolume().doubleValue()));
            }

            promptBuilder.append(String.format("\n현재가: ₩%,.0f원\n", currentPrice.doubleValue()));
            promptBuilder.append(String.format("현재 사용자의 가용 가상 예수금(현찰): ₩%,.0f원\n\n", cash));
            
            promptBuilder.append("### 실시간 주요 뉴스 헤드라인:\n");
            if (newsFeed.isEmpty()) {
                promptBuilder.append("- 현재 종목과 관련된 주요 헤드라인 기사가 없습니다.\n");
            } else {
                for (int i = 0; i < newsFeed.size(); i++) {
                    Map<String, String> n = newsFeed.get(i);
                    promptBuilder.append(String.format("%d. %s (발행시간: %s)\n", i + 1, n.get("title"), n.get("date")));
                }
            }

            promptBuilder.append("\n위의 퀀트 데이터(차트 추세)와 기사 정보(시장 여론 및 호악재 여부)를 종합적으로 분석해 아래 9가지 항목을 포함하는 융합 투자 보고서를 작성해줘.\n");
            promptBuilder.append("1. rating: 'RECOMMEND' (현재 매수 추천), 'HOLD' (관망/보류), 'WARNING' (경고/대피) 중 정확히 하나만 기재.\n");
            promptBuilder.append("2. recommendedPrice: 지지선 역할을 할 만한 합리적 진입 추천가 (₩ 단위 정수). 즉시 매수 추천 시 현재가 기재.\n");
            promptBuilder.append("3. reason: 기술적 차트 분석 및 뉴스를 토대로 한 판단 근거 한글 2~3줄 요약. (존댓말로 정중히 작성)\n");
            promptBuilder.append("4. newsSentiment: '호재 발생', '악재 우려', '시장 정체 및 중립' 중 정확히 하나만 선택.\n");
            promptBuilder.append("5. newsSummary: 뉴스 기사들의 뉘앙스를 종합해 여론과 심리상태를 설명해주는 한글 1~2줄 코멘트.\n");
            promptBuilder.append("6. alternativeStocks: 'HOLD'나 'WARNING' 시 대안으로 추천할 만한 코스피/코스닥 대형주 1~2종목의 코드와 종목명 제안 (JSON 배열 형식).\n");
            promptBuilder.append("7. recommendedParameters: 주가 수준과 변동성에 기초해 제안하는 자동매매 추천 설정값 하위 객체 (amount: 1회 매수금액 ₩ 10,000~500,000 정수, intervalMinutes: 매수주기 기본 1440, dropPercent: 낙폭과대 기준하락률 1.0~8.0 실수, stopLossPercent: 손절한도 1.5~6.0 실수, takeProfitPercent: 익절한도 3.0~15.0 실수).\n");
            promptBuilder.append("8. recommendedAmount: 사용자의 가상 예수금 규모 중 이 종목에 배분할 추천 1회 진입 약정 금액 (₩ 단위 정수). RECOMMEND 시 대략 예수금의 20% 금액, HOLD/WARNING 시 대략 예수금의 5% 내외를 추천하는 것을 권장.\n");
            promptBuilder.append("9. recommendedQuantity: recommendedAmount / recommendedPrice로 산출하여 실제 매수하게 되는 정수 형태의 추천 매수 주수 (최소 1주 이상).\n\n");
            
            promptBuilder.append("반드시 JSON 형식으로만 응답해야 하며, 그 외의 설명 텍스트나 markdown 코드 블록 기호(예: ```json)는 포함하지 말고 순수 JSON 문자열만 리턴해줘. 응답 JSON 구조:\n");
            promptBuilder.append("{\n");
            promptBuilder.append("  \"rating\": \"RECOMMEND\",\n");
            promptBuilder.append("  \"recommendedPrice\": 62000,\n");
            promptBuilder.append("  \"reason\": \"판단 근거 작성...\",\n");
            promptBuilder.append("  \"newsSentiment\": \"호재 발생\",\n");
            promptBuilder.append("  \"newsSummary\": \"뉴스 기사 심리 요약...\",\n");
            promptBuilder.append("  \"recommendedParameters\": {\"amount\": 50000, \"intervalMinutes\": 1440, \"dropPercent\": 4.5, \"stopLossPercent\": 3.0, \"takeProfitPercent\": 10.0},\n");
            promptBuilder.append("  \"recommendedAmount\": 100000,\n");
            promptBuilder.append("  \"recommendedQuantity\": 2,\n");
            promptBuilder.append("  \"alternativeStocks\": [{\"code\": \"000660\", \"name\": \"SK하이닉스\"}]\n");
            promptBuilder.append("}\n");

            // 2. HTTP Request Body 생성
            Map<String, Object> parts = Map.of("text", promptBuilder.toString());
            Map<String, Object> partsContainer = Map.of("parts", List.of(parts));
            Map<String, Object> requestBody = Map.of("contents", List.of(partsContainer));

            // 3. Gemini API 호출
            String responseStr = webClient.post()
                    .uri("/v1beta/models/gemini-1.5-flash:generateContent?key=" + geminiApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // 4. 응답 파싱 및 정제
            JsonNode root = objectMapper.readTree(responseStr);
            String aiText = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            aiText = aiText.replaceAll("```json", "")
                           .replaceAll("```", "")
                           .trim();

            Map<String, Object> result = objectMapper.readValue(aiText, Map.class);
            return result;

        } catch (Exception e) {
            System.err.println("Gemini AI API call failed: " + e.getMessage());
            return generateMockReport(symbol, name, candles, newsFeed, cash);
        }
    }

    /**
     * Gemini API 미설정 및 장애 상황을 우회하기 위한 퀀트 모의 투자 보고서 생성기 (뉴스 및 예수금 추천 수량 포함)
     */
    private Map<String, Object> generateMockReport(String symbol, String name, List<TossDto.CandleDto> candles, List<Map<String, String>> newsFeed, double cash) {
        Map<String, Object> mock = new HashMap<>();
        double currentPrice = candles.isEmpty() ? 50000.0 : candles.get(0).getClose().doubleValue();
        
        java.util.Random random = new java.util.Random(symbol.hashCode());
        int randomType = random.nextInt(3); // 0: RECOMMEND, 1: HOLD, 2: WARNING

        String rating = "HOLD";
        double recommPrice = currentPrice;
        String reason = "";
        String newsSentiment = "시장 정체 및 중립";
        String newsSummary = "";
        List<Map<String, String>> alts = new ArrayList<>();

        if (randomType == 0) {
            rating = "RECOMMEND";
            recommPrice = currentPrice;
            reason = String.format("최근 15일 간 %s의 일봉 거래량이 점진적으로 동반 상승하고 있으며, 주요 이평선이 정배열 구간에 진입하여 단기적 강세 흐름이 예상됩니다.", name);
            newsSentiment = "호재 발생";
            newsSummary = !newsFeed.isEmpty() 
                ? String.format("최근 기사 '%s' 등에서 해당 기업의 미래 성장 동력과 신규 수주 소식이 잇따르며 긍정적인 투자 분위기가 지배적입니다.", newsFeed.get(0).get("title"))
                : "해당 종목의 차세대 신성장 사업 진출 소식이 주도적으로 시장에 퍼지며 매수 우위의 심리가 확산되고 있습니다.";
        } else if (randomType == 1) {
            rating = "HOLD";
            recommPrice = Math.round(currentPrice * 0.95);
            reason = String.format("최근 주가 흐름이 좁은 박스권에서 거래량 감소와 함께 혼조세를 보이고 있습니다. 하방 지지선인 ₩%,.0f원 대 조정 시 진입하십시오.", recommPrice);
            newsSentiment = "시장 정체 및 중립";
            newsSummary = !newsFeed.isEmpty()
                ? String.format("최근 기사 '%s' 등의 흐름으로 보아, 대외 경기 지표 발표를 앞두고 투자자들의 눈치 보기 관망세가 짙어진 형국입니다.", newsFeed.get(0).get("title"))
                : "매크로 금리 변동성에 대비한 기관 투자자들의 차분한 포지션 조정이 이어지며 특별한 단기 정서 변화는 감지되지 않습니다.";
            
            alts.add(Map.of("code", "000660", "name", "SK하이닉스"));
            alts.add(Map.of("code", "373220", "name", "LG에너지솔루션"));
        } else {
            rating = "WARNING";
            recommPrice = Math.round(currentPrice * 0.91);
            reason = String.format("단기 급등에 따른 차익 실현 매물이 거래량과 함께 출회되며 데드크로스 징후가 포착되었습니다. 리스크 관리가 필요합니다.", recommPrice);
            newsSentiment = "악재 우려";
            newsSummary = !newsFeed.isEmpty()
                ? String.format("최근 기사 '%s'에 언급된 고평가 논란 및 부진 우려 기사가 투자 심리를 억누르고 있어 단기 추가 매도 압력이 제기됩니다.", newsFeed.get(0).get("title"))
                : "경쟁사 대비 약화된 시장 점유율 데이터와 대외 환경 악화 소식이 기사화되며 보수적 방어 태세 전환이 필요해 보입니다.";
            
            alts.add(Map.of("code", "005930", "name", "삼성전자"));
            alts.add(Map.of("code", "035420", "name", "NAVER"));
        }

        Map<String, Object> recParams = new HashMap<>();
        double allocationPercent = 5.0; // HOLD/WARNING 기본 5%
        if (randomType == 0) { // RECOMMEND
            allocationPercent = 20.0; // 매수 추천 시 20%
            recParams.put("amount", 100000);
            recParams.put("intervalMinutes", 1440);
            recParams.put("dropPercent", 3.5);
            recParams.put("stopLossPercent", 2.5);
            recParams.put("takeProfitPercent", 8.0);
        } else if (randomType == 1) { // HOLD
            recParams.put("amount", 50000);
            recParams.put("intervalMinutes", 1440);
            recParams.put("dropPercent", 5.0);
            recParams.put("stopLossPercent", 3.0);
            recParams.put("takeProfitPercent", 10.0);
        } else { // WARNING
            recParams.put("amount", 20000);
            recParams.put("intervalMinutes", 1440);
            recParams.put("dropPercent", 6.5);
            recParams.put("stopLossPercent", 4.0);
            recParams.put("takeProfitPercent", 12.0);
        }

        // 예수금 비중 계산
        double recommendedAmount = cash * (allocationPercent / 100.0);
        int recommendedQuantity = (int) Math.floor(recommendedAmount / recommPrice);
        if (recommendedQuantity < 1) {
            recommendedQuantity = 1;
        }

        mock.put("rating", rating);
        mock.put("recommendedPrice", (int) recommPrice);
        mock.put("reason", reason);
        mock.put("newsSentiment", newsSentiment);
        mock.put("newsSummary", newsSummary);
        mock.put("recommendedParameters", recParams);
        mock.put("recommendedAmount", (int) Math.round(recommendedAmount));
        mock.put("recommendedQuantity", recommendedQuantity);
        mock.put("alternativeStocks", alts);
        mock.put("mock", true);

        return mock;
    }
}
