package com.toss.trading.ai;

import com.toss.trading.toss.dto.TossDto;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiCommitteeService {

    private final GeminiAiService geminiAiService;
    private final OpenAiService openAiService;

    public AiCommitteeService(GeminiAiService geminiAiService, OpenAiService openAiService) {
        this.geminiAiService = geminiAiService;
        this.openAiService = openAiService;
    }

    /**
     * Gemini와 ChatGPT의 분석 결과를 융합하여 종합 합의 투자위원회 리포트를 생성합니다.
     */
    public Map<String, Object> getConsensusReport(String symbol, String name, List<TossDto.CandleDto> candles, double cash) {
        // 1. Gemini 분석 수집 (항상 수행)
        Map<String, Object> geminiReport = geminiAiService.analyzeStock(symbol, name, candles, cash);

        // 2. OpenAI 분석 수집 (설정된 경우에만)
        Map<String, Object> openAiReport = null;
        if (openAiService.isConfigured()) {
            openAiReport = openAiService.analyzeStock(symbol, name, candles, cash);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("gemini", geminiReport);
        result.put("openai", openAiReport);

        // 3. 합의(Consensus) 도출
        if (openAiReport != null) {
            String gRating = String.valueOf(geminiReport.getOrDefault("rating", "HOLD")).toUpperCase();
            String oRating = String.valueOf(openAiReport.getOrDefault("rating", "HOLD")).toUpperCase();

            String consensusRating = "HOLD";
            int consensusScore = 50;

            // 투표 기반 합의
            if ("RECOMMEND".equals(gRating) && "RECOMMEND".equals(oRating)) {
                consensusRating = "RECOMMEND";
                consensusScore = 100;
            } else if ("WARNING".equals(gRating) && "WARNING".equals(oRating)) {
                consensusRating = "WARNING";
                consensusScore = 0;
            } else if ("RECOMMEND".equals(gRating) && "HOLD".equals(oRating)) {
                consensusRating = "HOLD";
                consensusScore = 75;
            } else if ("HOLD".equals(gRating) && "RECOMMEND".equals(oRating)) {
                consensusRating = "HOLD";
                consensusScore = 75;
            } else if ("WARNING".equals(gRating) || "WARNING".equals(oRating)) {
                consensusRating = "HOLD";
                consensusScore = 25; // 한쪽이 WARNING이면 보수적으로 관망
            }

            result.put("consensusRating", consensusRating);
            result.put("consensusScore", consensusScore);
            result.put("mode", "DUAL");
        } else {
            // 단일 LLM(Gemini) 모드인 경우
            String rating = String.valueOf(geminiReport.getOrDefault("rating", "HOLD")).toUpperCase();
            int score = 50;
            if ("RECOMMEND".equals(rating)) score = 100;
            else if ("WARNING".equals(rating)) score = 0;

            result.put("consensusRating", rating);
            result.put("consensusScore", score);
            result.put("mode", "SINGLE");
        }

        return result;
    }
}
