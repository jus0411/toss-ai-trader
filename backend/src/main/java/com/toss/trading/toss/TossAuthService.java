package com.toss.trading.toss;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@Service
public class TossAuthService {

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;
    private final String tradingMode;

    private String accessToken;
    private LocalDateTime tokenExpiryTime;

    public TossAuthService(
            WebClient.Builder webClientBuilder,
            @Value("${trading.toss.base-url}") String baseUrl,
            @Value("${trading.toss.client-id}") String clientId,
            @Value("${trading.toss.client-secret}") String clientSecret,
            @Value("${trading.mode}") String tradingMode) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.clientId = clientId != null ? clientId.trim() : null;
        this.clientSecret = clientSecret != null ? clientSecret.trim() : null;
        this.tradingMode = tradingMode;
    }

    /**
     * 유효한 Access Token을 반환합니다. 만료되었거나 없을 경우 새로 발급합니다.
     */
    public synchronized String getAccessToken() {
        // 가상 모드(Mock Mode)이면서 실제 Client ID가 설정되어 있지 않은 경우에만 가짜 토큰을 반환합니다.
        boolean hasRealKey = clientId != null && !clientId.isEmpty() && !clientId.contains("your_toss_client_id_here");
        
        if ("mock".equalsIgnoreCase(tradingMode) && !hasRealKey) {
            return "mock-access-token-for-testing-only";
        }

        if (accessToken == null || tokenExpiryTime == null || LocalDateTime.now().isAfter(tokenExpiryTime.minusMinutes(10))) {
            refreshAccessToken();
        }
        return accessToken;
    }

    private void refreshAccessToken() {
        System.out.println("Refreshing Toss OpenAPI Access Token via Form URL-Encoded and Basic Auth...");
        try {
            String credentials = clientId + ":" + clientSecret;
            String authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());

            Map response = webClient.post()
                    .uri("/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header("Authorization", authHeader)
                    .body(BodyInserters.fromFormData("grant_type", "client_credentials"))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("access_token")) {
                this.accessToken = (String) response.get("access_token");
                int expiresIn = ((Number) response.get("expires_in")).intValue();
                this.tokenExpiryTime = LocalDateTime.now().plusSeconds(expiresIn);
                System.out.println("Toss Access Token refreshed successfully. Expires at: " + tokenExpiryTime);
            } else {
                throw new RuntimeException("Failed to refresh token: empty or invalid response");
            }
        } catch (Exception e) {
            System.err.println("Error refreshing access token (Falling back to mock mode): " + e.getMessage());
            // 키 인증 오류나 네트워크 실패 시 전체 시스템이 뻗는 현상 방지
            this.accessToken = "mock-access-token-for-testing-only";
            this.tokenExpiryTime = LocalDateTime.now().plusHours(1);
        }
    }
}
