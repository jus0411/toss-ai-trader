package com.toss.trading.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RetailSentimentService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final NewsSearchService newsSearchService;

    @Value("${trading.gemini.api-key:}")
    private String geminiApiKey;

    public RetailSentimentService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, NewsSearchService newsSearchService) {
        this.webClient = webClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.objectMapper = objectMapper;
        this.newsSearchService = newsSearchService;
    }

    /**
     * 특정 종목의 실시간 여론 동향 분석 (희망 vs 공포 비율 및 종합 점수)
     */
    public Map<String, Object> analyzeSentiment(String stockName) {
        List<Map<String, String>> newsFeed = newsSearchService.searchStockNews(stockName);

        if (geminiApiKey == null || geminiApiKey.trim().isEmpty() || "your_gemini_key_here".equals(geminiApiKey)) {
            return generateMockSentiment(stockName, newsFeed);
        }

        try {
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append(String.format("너는 금융 심리 분석 전문가이다. 다음은 최근 주식 시장에서 %s 종목과 관련되어 발행된 주요 뉴스 기사 제목들이다. 이 기사들의 뉘앙스를 종합적으로 분석해 주주들의 투자 심리 상태를 요약해줘.\n\n", stockName));
            
            promptBuilder.append("### 뉴스 헤드라인 목록:\n");
            for (int i = 0; i < newsFeed.size(); i++) {
                promptBuilder.append(String.format("%d. %s\n", i + 1, newsFeed.get(i).get("title")));
            }

            promptBuilder.append("\n위 기사들의 감정 뉘앙스를 분석하여 아래 항목들을 포함한 JSON 형식의 여론 분석 리포트를 작성해줘.\n");
            promptBuilder.append("1. score: 0부터 100까지의 종합 투자 심리 감성 점수 (50이 중립, 100에 가까울수록 극단적인 긍정/광기, 0에 가까울수록 투매/공포)\n");
            promptBuilder.append("2. positivePercent: 호재성 기사 비율 (0 ~ 100 사이 정수)\n");
            promptBuilder.append("3. negativePercent: 악재성 기사 비율 (0 ~ 100 사이 정수, positivePercent + negativePercent = 100이 되도록 비율 환산)\n");
            promptBuilder.append("4. summary: 전체 여론 심리 및 동향을 설명하는 1줄의 정중한 요약 코멘트.\n\n");
            promptBuilder.append("반드시 markdown 코드 블록 기호 없이 순수 JSON 포맷으로만 응답해줘. 응답 구조 예시:\n");
            promptBuilder.append("{\n");
            promptBuilder.append("  \"score\": 75,\n");
            promptBuilder.append("  \"positivePercent\": 75,\n");
            promptBuilder.append("  \"negativePercent\": 25,\n");
            promptBuilder.append("  \"summary\": \"최근 3분기 실적 개선 호조로 주주들 사이에 낙관적인 장세 전망이 확산되고 있습니다.\"\n");
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
            System.err.println("Gemini Sentiment Analysis API call failed: " + e.getMessage());
            return generateMockSentiment(stockName, newsFeed);
        }
    }

    private Map<String, Object> generateMockSentiment(String stockName, List<Map<String, String>> newsFeed) {
        Map<String, Object> mock = new HashMap<>();
        java.util.Random random = new java.util.Random(stockName.hashCode());
        
        int positive = 40 + random.nextInt(35); // 40 ~ 75
        int negative = 100 - positive;
        int score = positive + (random.nextInt(10) - 5);

        mock.put("score", score);
        mock.put("positivePercent", positive);
        mock.put("negativePercent", negative);
        mock.put("summary", String.format("최근 %s 관련 소식들에 비추어 보았을 때, 중장기적 성장성에 대한 주주들의 기대감과 일부 단기 가격 조정에 대한 우려가 팽팽히 맞서는 상황입니다.", stockName));
        return mock;
    }
}
