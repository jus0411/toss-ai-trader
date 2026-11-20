package com.toss.trading.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toss.trading.toss.dto.TossDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final NewsSearchService newsSearchService;

    @Value("${trading.groq.api-key:}")
    private String groqApiKey;

    public OpenAiService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, NewsSearchService newsSearchService) {
        // Groq API는 OpenAI와 100% 규격이 호환되는 엔드포인트를 제공합니다.
        this.webClient = webClientBuilder.baseUrl("https://api.groq.com/openai/v1").build();
        this.objectMapper = objectMapper;
        this.newsSearchService = newsSearchService;
    }

    public boolean isConfigured() {
        return groqApiKey != null && !groqApiKey.trim().isEmpty() && !"your_groq_api_key_here".equals(groqApiKey);
    }

    /**
     * 동일한 차트/뉴스 기반으로 Meta Llama 3.1 70B 모델의 의견을 획득합니다 (Groq API 무료 한도 연동).
     */
    public Map<String, Object> analyzeStock(String symbol, String name, List<TossDto.CandleDto> candles, double cash) {
        if (!isConfigured()) {
            return null; // Groq API 키가 없으면 AI 투자위원회가 Gemini 단일 모드로 부드럽게 작동함.
        }

        try {
            List<Map<String, String>> newsFeed = newsSearchService.searchStockNews(name);
            BigDecimal currentPrice = candles.isEmpty() ? BigDecimal.ZERO : candles.get(0).getClose();

            // 1. 프롬프트 작성
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append(String.format("너는 금융 시장의 매크로 및 차트 분석 AI 봇이다. 다음은 주식 %s (%s)의 최근 15일 간의 일봉 데이터(종가 및 거래량), 최근 구글 뉴스 기사 목록, 그리고 현재 사용자가 쥐고 있는 가용 예수금(현찰)이다.\n\n", name, symbol));
            
            promptBuilder.append("### 최근 캔들 시세:\n");
            for (int i = Math.min(candles.size(), 15) - 1; i >= 0; i--) {
                TossDto.CandleDto c = candles.get(i);
                promptBuilder.append(String.format("- 날짜: %s, 종가: ₩%,.0f원, 거래량: %,.0f주\n", 
                        c.getTimestamp().split("T")[0], c.getClose().doubleValue(), c.getVolume().doubleValue()));
            }

            promptBuilder.append(String.format("\n현재가: ₩%,.0f원\n", currentPrice.doubleValue()));
            promptBuilder.append(String.format("사용자의 예수금 잔고: ₩%,.0f원\n\n", cash));
            
            promptBuilder.append("### 주요 뉴스:\n");
            for (int i = 0; i < newsFeed.size(); i++) {
                promptBuilder.append(String.format("%d. %s\n", i + 1, newsFeed.get(i).get("title")));
            }

            promptBuilder.append("\n위의 데이터를 기반으로 투자 분석 보고서를 JSON 객체로 작성해줘.\n");
            promptBuilder.append("응답 JSON 구조:\n");
            promptBuilder.append("{\n");
            promptBuilder.append("  \"rating\": \"RECOMMEND\" or \"HOLD\" or \"WARNING\",\n");
            promptBuilder.append("  \"recommendedPrice\": 62000 (추천 진입가 ₩ 단위),\n");
            promptBuilder.append("  \"reason\": \"판단 근거 2~3줄 요약 (존댓말 한글)\",\n");
            promptBuilder.append("  \"newsSentiment\": \"호재 발생\" or \"악재 우려\" or \"시장 정체 및 중립\",\n");
            promptBuilder.append("  \"newsSummary\": \"뉴스 심리 요약 1줄\"\n");
            promptBuilder.append("}\n");

            // 2. Body 구성
            Map<String, Object> message = Map.of(
                    "role", "user",
                    "content", promptBuilder.toString()
            );
            Map<String, Object> responseFormat = Map.of("type", "json_object");
            
            // Groq의 고성능 대형 오픈소스 모델인 Llama 3.1 70B 지정
            Map<String, Object> requestBody = Map.of(
                    "model", "llama-3.1-70b-versatile",
                    "messages", List.of(message),
                    "response_format", responseFormat
            );

            // 3. HTTP API 전송 (Groq API Key 헤더 인증)
            String responseStr = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + groqApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseStr);
            String aiJsonText = root.path("choices").get(0).path("message").path("content").asText();

            Map<String, Object> result = objectMapper.readValue(aiJsonText, Map.class);
            return result;

        } catch (Exception e) {
            System.err.println("Groq Llama 3.1 API call failed: " + e.getMessage());
            return null;
        }
    }
}
