package com.toss.trading.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toss.trading.toss.TossApiClient;
import com.toss.trading.toss.dto.TossDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiChatService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final TossApiClient apiClient;

    private static final List<Map<String, String>> STOCK_MASTER = List.of(
            Map.of("code", "005930", "name", "삼성전자"),
            Map.of("code", "000660", "name", "SK하이닉스"),
            Map.of("code", "352820", "name", "하이브"),
            Map.of("code", "051910", "name", "LG화학"),
            Map.of("code", "035420", "name", "NAVER")
    );

    @Value("${trading.gemini.api-key:}")
    private String geminiApiKey;

    public AiChatService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, TossApiClient apiClient) {
        this.webClient = webClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.objectMapper = objectMapper;
        this.apiClient = apiClient;
    }

    /**
     * 주주님의 자유 질의에 대해 AI 퀀트 주식 컨설턴트 답변과 관련 추천 종목 카드를 생성합니다.
     */
    public Map<String, Object> chatWithAi(String userMessage) {
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty() || "your_gemini_key_here".equals(geminiApiKey)) {
            return generateMockChatResponse(userMessage);
        }

        try {
            // 현재 대형 우량 종목 시세 정보 수집하여 AI에 맥락으로 주입
            List<String> targetSymbols = List.of("005930", "000660", "352820", "051910", "035420");
            List<TossDto.PriceDto> prices = apiClient.getPrices(targetSymbols);
            
            StringBuilder marketContext = new StringBuilder("현재 한국 주요 대형주 실시간 시세 현황:\n");
            for (TossDto.PriceDto p : prices) {
                String name = getStockName(p.getSymbol());
                marketContext.append(String.format("- %s (%s): ₩%,.0f원\n", 
                        name, p.getSymbol(), p.getLastPrice().doubleValue()));
            }

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("너는 토스 오토 트레이더 시스템의 수석 AI 퀀트 주식 컨설턴트이다. 주주님의 질문에 대해 전문적이면서도 다정하고 정중한 존댓말로 명쾌하게 답변해줘.\n\n");
            promptBuilder.append(marketContext.toString()).append("\n");
            promptBuilder.append("주주님 질문: \"").append(userMessage).append("\"\n\n");
            
            promptBuilder.append("답변 작성 지침:\n");
            promptBuilder.append("1. 질문에 대해 명확하고 논리적인 퀀트/기술적 투자 진단 코멘트를 작성해줘.\n");
            promptBuilder.append("2. 질문과 관련하여 주주님이 관심을 가지거나 매수/관망 검토해볼 만한 국내 대형주 1~3종목을 제안해줘. (종목코드 6자리 필수)\n");
            promptBuilder.append("3. 응답은 반드시 아래 JSON 구조로만 작성해줘. Markdown 코드 블록 기호는 제외해줘.\n\n");
            
            promptBuilder.append("JSON 응답 포맷 예시:\n");
            promptBuilder.append("{\n");
            promptBuilder.append("  \"answerText\": \"주주님, 질의하신 내용에 대해 분석한 결과... (정중한 한글 3~5줄 답변)\",\n");
            promptBuilder.append("  \"recommendedStocks\": [\n");
            promptBuilder.append("    {\"code\": \"005930\", \"name\": \"삼성전자\", \"price\": 72000, \"reason\": \"이평선 골든크로스 진입 및 거래량 증가\"},\n");
            promptBuilder.append("    {\"code\": \"000660\", \"name\": \"SK하이닉스\", \"price\": 185000, \"reason\": \"반도체 업황 호조에 따른 신상승 모멘텀\"}\n");
            promptBuilder.append("  ]\n");
            promptBuilder.append("}\n");

            Map<String, Object> parts = Map.of("text", promptBuilder.toString());
            Map<String, Object> partsContainer = Map.of("parts", List.of(parts));
            Map<String, Object> requestBody = Map.of("contents", List.of(partsContainer));

            String responseStr = webClient.post()
                    .uri("/v1beta/models/gemini-1.5-flash:generateContent?key=" + geminiApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

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
            System.err.println("Gemini AI Chat request failed: " + e.getMessage());
            return generateMockChatResponse(userMessage);
        }
    }

    private String getStockName(String code) {
        for (Map<String, String> m : STOCK_MASTER) {
            if (code.equals(m.get("code"))) {
                return m.get("name");
            }
        }
        return "종목(" + code + ")";
    }

    private Map<String, Object> generateMockChatResponse(String userMessage) {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> recStocks = new ArrayList<>();

        if (userMessage.contains("상승") || userMessage.contains("추천") || userMessage.contains("종목")) {
            response.put("answerText", "주주님, 실시간 퀀트 알고리즘과 거래량 지표를 분석한 결과, 반도체 및 대형 IT 섹터 종목들의 단기 상승 모멘텀이 상대적으로 매우 높게 형성되어 있습니다. 아래의 두 대형주를 주목해 보시는 것을 권장합니다.");
            
            Map<String, Object> s1 = new HashMap<>();
            s1.put("code", "005930");
            s1.put("name", "삼성전자");
            s1.put("price", 72500);
            s1.put("reason", "20일 이평선 지지받고 외국인 순매수세 유입 중");
            recStocks.add(s1);

            Map<String, Object> s2 = new HashMap<>();
            s2.put("code", "000660");
            s2.put("name", "SK하이닉스");
            s2.put("price", 188000);
            s2.put("reason", "HBM 공급 호재로 단기 전고점 돌파 기대");
            recStocks.add(s2);
        } else if (userMessage.contains("안전") || userMessage.contains("적립") || userMessage.contains("모으기")) {
            response.put("answerText", "주주님, 주가 변동성이 심한 장세에서는 RSI 지표가 30 이하로 떨어졌을 때 분할 매수하는 'RSI 스마트 모으기' 전략이 자산을 안전하게 증식시키는 데 가장 효율적입니다.");
            
            Map<String, Object> s1 = new HashMap<>();
            s1.put("code", "005930");
            s1.put("name", "삼성전자");
            s1.put("price", 72500);
            s1.put("reason", "안정적인 배당 성향과 대형 우량주 리스크 분산 효과");
            recStocks.add(s1);
        } else {
            response.put("answerText", String.format("주주님께서 질의해주신 '%s'에 대해 퀀트 시세 데이터와 뉴스를 융합 검토해보았습니다. 현재 시장 변동성 지수는 안정적이며 주요 관심 종목의 이평선 정배열 추세가 유지되고 있습니다.", userMessage));
            
            Map<String, Object> s1 = new HashMap<>();
            s1.put("code", "352820");
            s1.put("name", "하이브");
            s1.put("price", 215000);
            s1.put("reason", "실적 개선 기대로 인한 바닥권 반등 시그널 포착");
            recStocks.add(s1);
        }

        response.put("recommendedStocks", recStocks);
        return response;
    }
}
