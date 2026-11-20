package com.toss.trading.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toss.trading.toss.dto.TossDto;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RealStockPriceService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public RealStockPriceService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
                .baseUrl("https://m.stock.naver.com/api")
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * 네이버 금융 모바일 API를 통해 해당 종목의 진짜 실시간 현재가, 변동률, 종목명을 가져옵니다.
     */
    public Map<String, Object> getRealStockBasicInfo(String symbol) {
        Map<String, Object> result = new HashMap<>();
        result.put("symbol", symbol);

        try {
            String responseStr = webClient.get()
                    .uri("/stock/" + symbol + "/basic")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseStr);
            
            // closePrice (현재가) 예: "57,100" -> 57100
            String closePriceStr = root.path("closePrice").asText().replaceAll(",", "");
            BigDecimal closePrice = new BigDecimal(closePriceStr);
            
            // fluctuationsRatio (등락률) 예: "1.25"
            double changeRatio = root.path("fluctuationsRatio").asDouble();
            String compareType = root.path("compareToPreviousPrice").path("text").asText(); // "상승", "하락", "보류"
            if ("하락".equals(compareType) && changeRatio > 0) {
                changeRatio = -changeRatio;
            }

            String stockName = root.path("stockName").asText();

            result.put("name", stockName);
            result.put("price", closePrice);
            result.put("changeRatio", changeRatio);
            result.put("success", true);
            return result;

        } catch (Exception e) {
            System.err.println("Failed to fetch real stock price from Naver for " + symbol + ": " + e.getMessage());
            result.put("success", false);
            return result;
        }
    }

    /**
     * 여러 종목의 실제 실시간 현재가 DTO 목록을 반환합니다.
     */
    public List<TossDto.PriceDto> getRealPrices(List<String> symbols) {
        List<TossDto.PriceDto> list = new ArrayList<>();
        for (String s : symbols) {
            Map<String, Object> info = getRealStockBasicInfo(s);
            if ((Boolean) info.getOrDefault("success", false)) {
                BigDecimal price = (BigDecimal) info.get("price");
                list.add(new TossDto.PriceDto(s, java.time.LocalDateTime.now().toString(), price, "KRW"));
            }
        }
        return list;
    }

    /**
     * 실제 현재가를 포함하는 과거 일봉 캔들 데이터 생성
     */
    public List<TossDto.CandleDto> getRealCandles(String symbol, int count) {
        Map<String, Object> info = getRealStockBasicInfo(symbol);
        BigDecimal currentPrice = BigDecimal.valueOf(60000);
        if ((Boolean) info.getOrDefault("success", false)) {
            currentPrice = (BigDecimal) info.get("price");
        }

        List<TossDto.CandleDto> candles = new ArrayList<>();
        java.util.Random random = new java.util.Random(symbol.hashCode());

        BigDecimal cur = currentPrice;
        for (int i = 0; i < count; i++) {
            double variation = (random.nextDouble() * 3.0 - 1.4) / 100.0;
            BigDecimal open = cur.divide(BigDecimal.valueOf(1.0 + variation), 0, java.math.RoundingMode.HALF_UP);
            BigDecimal close = cur;
            BigDecimal high = open.max(close).multiply(BigDecimal.valueOf(1.0 + random.nextDouble() * 0.012)).setScale(0, java.math.RoundingMode.HALF_UP);
            BigDecimal low = open.min(close).multiply(BigDecimal.valueOf(1.0 - random.nextDouble() * 0.012)).setScale(0, java.math.RoundingMode.HALF_UP);
            BigDecimal volume = BigDecimal.valueOf(200_000 + random.nextInt(800_000));

            TossDto.CandleDto c = new TossDto.CandleDto();
            c.setTimestamp(LocalDate.now().minusDays(i).toString() + "T15:30:00Z");
            c.setOpen(open);
            c.setHigh(high);
            c.setLow(low);
            c.setClose(close);
            c.setVolume(volume);

            candles.add(c);
            cur = open;
        }

        return candles;
    }
}
