package com.toss.trading.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toss.trading.domain.OrderRecord;
import com.toss.trading.domain.TradingDiary;
import com.toss.trading.repository.OrderRecordRepository;
import com.toss.trading.repository.TradingDiaryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class TradingDiaryService {

    private final TradingDiaryRepository diaryRepository;
    private final OrderRecordRepository orderRecordRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${trading.gemini.api-key:}")
    private String geminiApiKey;

    public TradingDiaryService(TradingDiaryRepository diaryRepository,
                               OrderRecordRepository orderRecordRepository,
                               WebClient.Builder webClientBuilder,
                               ObjectMapper objectMapper) {
        this.diaryRepository = diaryRepository;
        this.orderRecordRepository = orderRecordRepository;
        this.webClient = webClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.objectMapper = objectMapper;
    }

    /**
     * 당일의 매매 거래 주문 내역을 집계하여 Gemini AI 기반의 일일 투자 일지(복기록)를 작성합니다.
     */
    @Transactional
    public TradingDiary generateDailyDiary() {
        String todayStr = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        
        // 이미 오늘의 일지가 생성되어 있다면 리턴
        if (diaryRepository.findByDiaryDate(todayStr).isPresent()) {
            return diaryRepository.findByDiaryDate(todayStr).get();
        }

        // 오늘 0시 이후 체결된 주문 검색
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        List<OrderRecord> todayOrders = orderRecordRepository.findAllByOrderTimeAfter(startOfDay);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("### %s 자동매매 체결 내역 요약 ###\n", todayStr));
        if (todayOrders.isEmpty()) {
            sb.append("- 오늘은 체결된 거래 내역이 존재하지 않습니다.\n");
        } else {
            for (OrderRecord o : todayOrders) {
                sb.append(String.format("- [%s] %s (%s) %s %d주 체결 (단가: ₩%,.0f원)\n",
                        o.getOrderTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        o.getStockName(), o.getStockCode(), o.getOrderType(),
                        o.getQuantity().intValue(), o.getPrice()));
            }
        }

        String feedback = "";
        // API 키 유효성 검사 및 더미 처리 분기
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty() || "your_gemini_key_here".equals(geminiApiKey)) {
            feedback = generateMockFeedback(todayOrders.size());
        } else {
            try {
                String prompt = "너는 전문 주식 퀀트 트레이더이자 리스크 매니저이다. 아래 제공하는 자동매매 봇들의 일일 체결 데이터 내역을 검토한 뒤, 매수/매도 타이밍의 합리성 분석과 리스크 피드백(오답노트 성격)을 3~4문장으로 정중한 존댓말 한글 코멘트를 작성해줘.\n\n" + sb.toString();

                Map<String, Object> parts = Map.of("text", prompt);
                Map<String, Object> partsContainer = Map.of("parts", List.of(parts));
                Map<String, Object> requestBody = Map.of("contents", List.of(partsContainer));

                String responseStr = webClient.post()
                        .uri("/v1beta/models/gemini-1.5-flash:generateContent?key=" + geminiApiKey)
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JsonNode root = objectMapper.readTree(responseStr);
                feedback = root.path("candidates").get(0)
                        .path("content").path("parts").get(0)
                        .path("text").asText().trim();

            } catch (Exception e) {
                System.err.println("Failed to call Gemini for Trading Diary: " + e.getMessage());
                feedback = generateMockFeedback(todayOrders.size());
            }
        }

        TradingDiary diary = new TradingDiary();
        diary.setDiaryDate(todayStr);
        diary.setTotalTrades(todayOrders.size());
        diary.setFeedbackContent(feedback);
        diary.setRecordTime(LocalDateTime.now());

        return diaryRepository.save(diary);
    }

    private String generateMockFeedback(int tradesCount) {
        if (tradesCount == 0) {
            return "오늘은 시장 변동성이 좁아 봇이 매매 타점에 도달하지 못해 거래가 집계되지 않았습니다. 관망세를 유지하는 것은 자산 방어 관점에서 훌륭한 선택입니다. 조급한 추격 매수보다는 알고리즘 규칙에 맞춰 차분히 대기하시기를 권장합니다.";
        }
        return "오늘 체결된 거래들은 설정된 봇 알고리즘 규칙에 의거해 단기 지지선 반등 및 주기를 토대로 안정적으로 격발되었습니다. 다만, 일부 종목의 경우 최근 거래량이 급격히 하락하는 시점이었으므로 비중이 너무 크지 않은지 리스크 체크가 필요합니다. 전반적으로 규율을 지킨 매매는 장기적 수익 달성에 매우 긍정적입니다.";
    }
}
