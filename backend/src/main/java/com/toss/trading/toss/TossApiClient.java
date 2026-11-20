package com.toss.trading.toss;

import com.toss.trading.toss.dto.TossDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Component
public class TossApiClient {

    private final WebClient webClient;
    private final TossAuthService authService;
    private final String tradingMode;
    private final com.toss.trading.ai.RealStockPriceService realStockPriceService;

    private Long cachedAccountSeq;

    private final java.util.Map<String, BigDecimal> dummyPriceMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Random random = new java.util.Random();

    public TossApiClient(
            WebClient.Builder webClientBuilder,
            TossAuthService authService,
            @Value("${trading.toss.base-url}") String baseUrl,
            @Value("${trading.mode}") String tradingMode,
            com.toss.trading.ai.RealStockPriceService realStockPriceService) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.authService = authService;
        this.tradingMode = tradingMode;
        this.realStockPriceService = realStockPriceService;
    }

    /**
     * 주주님의 토스증권 OpenAPI 자격 증명(tsck_live_/tssk_live_)을 직접 활용하여 공식 시세 API를 최우선으로 호출합니다.
     */
    public List<TossDto.PriceDto> getPrices(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 토스증권 공식 OpenAPI /api/v1/prices 엔드포인트 최우선 직통 호출
        try {
            String token = authService.getAccessToken();
            if (token != null && !token.startsWith("mock-access-token")) {
                String symbolsParam = String.join(",", symbols);
                TossDto.PriceListResponse response = webClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/api/v1/prices")
                                .queryParam("symbols", symbolsParam)
                                .build())
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(TossDto.PriceListResponse.class)
                        .block();

                if (response != null && response.getResult() != null && !response.getResult().isEmpty()) {
                    return response.getResult();
                }
            }
        } catch (Exception e) {
            System.err.println("Toss OpenAPI getPrices direct call note: " + e.getMessage());
        }

        // 2. 보조 실시간 시세 연동 폴백
        try {
            List<TossDto.PriceDto> realPrices = realStockPriceService.getRealPrices(symbols);
            if (realPrices != null && !realPrices.isEmpty()) {
                return realPrices;
            }
        } catch (Exception e) {
            System.err.println("Fallback price service note: " + e.getMessage());
        }

        // 3. 테스트용 폴백 시세
        return symbols.stream()
                .map(symbol -> {
                    BigDecimal lastPrice = dummyPriceMap.computeIfAbsent(symbol, s -> {
                        if ("005930".equals(s)) return BigDecimal.valueOf(57100);
                        if ("000660".equals(s)) return BigDecimal.valueOf(163200);
                        return BigDecimal.valueOf(30000 + random.nextInt(40000));
                    });
                    return new TossDto.PriceDto(symbol, LocalDateTime.now().toString(), lastPrice, "KRW");
                })
                .toList();
    }

    /**
     * 사용자의 첫 번째 계좌 식별자(accountSeq)를 가져오거나 캐싱된 값을 반환합니다.
     */
    public synchronized Long getOrFetchAccountSeq() {
        if ("mock".equalsIgnoreCase(tradingMode)) {
            return 999999999L; // Mock 계좌 식별자
        }

        if (cachedAccountSeq != null) {
            return cachedAccountSeq;
        }

        try {
            TossDto.AccountListResponse response = webClient.get()
                    .uri("/api/v1/accounts")
                    .header("Authorization", "Bearer " + authService.getAccessToken())
                    .retrieve()
                    .bodyToMono(TossDto.AccountListResponse.class)
                    .block();

            if (response != null && response.getResult() != null && !response.getResult().isEmpty()) {
                cachedAccountSeq = response.getResult().get(0).getAccountSeq();
                System.out.println("Toss AccountSeq cached: " + cachedAccountSeq);
                return cachedAccountSeq;
            }
        } catch (Exception e) {
            System.err.println("Error fetching account seq from Toss API: " + e.getMessage());
        }

        throw new RuntimeException("Toss AccountSeq could not be retrieved. Please check your credentials.");
    }

    /**
     * 계좌 보유 주식 현황을 조회합니다.
     */
    public TossDto.HoldingsOverviewDto getHoldings() {
        if ("mock".equalsIgnoreCase(tradingMode)) {
            return null; // Mock 일 경우 DB에서 가상 보유 데이터를 읽어야 하므로 null 반환 후 서비스 단에서 가상데이터 매핑
        }

        try {
            Long accountSeq = getOrFetchAccountSeq();
            TossDto.HoldingsResponse response = webClient.get()
                    .uri("/api/v1/holdings")
                    .header("Authorization", "Bearer " + authService.getAccessToken())
                    .header("X-Tossinvest-Account", String.valueOf(accountSeq))
                    .retrieve()
                    .bodyToMono(TossDto.HoldingsResponse.class)
                    .block();

            return response != null ? response.getResult() : null;
        } catch (Exception e) {
            System.err.println("Error fetching holdings from Toss API: " + e.getMessage());
            return null;
        }
    }

    /**
     * 매수/매도 주문을 생성(실행)합니다.
     */
    public TossDto.OrderResultDto createOrder(TossDto.OrderCreateRequest request) {
        if ("mock".equalsIgnoreCase(tradingMode)) {
            throw new IllegalStateException("Cannot execute actual order in mock mode");
        }

        try {
            Long accountSeq = getOrFetchAccountSeq();
            TossDto.OrderResponse response = webClient.post()
                    .uri("/api/v1/orders")
                    .header("Authorization", "Bearer " + authService.getAccessToken())
                    .header("X-Tossinvest-Account", String.valueOf(accountSeq))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(TossDto.OrderResponse.class)
                    .block();

            return response != null ? response.getResult() : null;
        } catch (Exception e) {
            System.err.println("Error creating order on Toss API: " + e.getMessage());
            throw new RuntimeException("Toss Order API Execution Failed: " + e.getMessage(), e);
        }
    }

    /**
     * 주주님의 토스증권 OpenAPI 자격 증명을 사용하여 토스 공식 캔들 시세 API를 최우선으로 호출합니다.
     */
    public List<TossDto.CandleDto> getCandles(String symbol, String interval, int count) {
        // 1. 토스증권 공식 OpenAPI /api/v1/candles 엔드포인트 최우선 직통 호출
        try {
            String token = authService.getAccessToken();
            if (token != null && !token.startsWith("mock-access-token")) {
                TossDto.CandleListResponse response = webClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/api/v1/candles")
                                .queryParam("symbol", symbol)
                                .queryParam("interval", interval)
                                .queryParam("count", count)
                                .build())
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .bodyToMono(TossDto.CandleListResponse.class)
                        .block();

                if (response != null && response.getResult() != null && response.getResult().getCandles() != null && !response.getResult().getCandles().isEmpty()) {
                    return response.getResult().getCandles();
                }
            }
        } catch (Exception e) {
            System.err.println("Toss OpenAPI getCandles direct call note: " + e.getMessage());
        }

        // 2. 보조 캔들 시세 연동
        try {
            List<TossDto.CandleDto> realCandles = realStockPriceService.getRealCandles(symbol, count);
            if (realCandles != null && !realCandles.isEmpty()) {
                return realCandles;
            }
        } catch (Exception e) {
            System.err.println("Fallback candle service note: " + e.getMessage());
        }

        // 3. 테스트용 시뮬레이션 캔들
        BigDecimal basePrice = dummyPriceMap.computeIfAbsent(symbol, s -> BigDecimal.valueOf(57100));
        List<TossDto.CandleDto> dummyCandles = new java.util.ArrayList<>();
        BigDecimal curClose = basePrice;
        for (int i = 0; i < count; i++) {
            double changePct = (random.nextDouble() * 4.0 - 2.0) / 100.0;
            BigDecimal open = curClose.divide(BigDecimal.valueOf(1.0 + changePct), 0, RoundingMode.HALF_UP);
            BigDecimal close = curClose;
            
            BigDecimal high = open.max(close).multiply(BigDecimal.valueOf(1.0 + random.nextDouble() * 0.015)).setScale(0, RoundingMode.HALF_UP);
            BigDecimal low = open.min(close).multiply(BigDecimal.valueOf(1.0 - random.nextDouble() * 0.015)).setScale(0, RoundingMode.HALF_UP);
            BigDecimal volume = BigDecimal.valueOf(100_000 + random.nextInt(900_000));

            TossDto.CandleDto candle = new TossDto.CandleDto();
            candle.setTimestamp(LocalDate.now().minusDays(i).toString() + "T15:30:00Z");
            candle.setOpen(open);
            candle.setHigh(high);
            candle.setLow(low);
            candle.setClose(close);
            candle.setVolume(volume);

            dummyCandles.add(candle);
            curClose = open;
        }
        return dummyCandles;
    }
}
