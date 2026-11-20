package com.toss.trading.config;

import com.toss.trading.toss.TossOrderService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class TradingConfig {

    @Bean
    @Primary
    public TossOrderService tossOrderService(
            @Value("${trading.mode}") String mode,
            @Qualifier("mockTossOrderService") TossOrderService mockTossOrderService,
            @Qualifier("realTossOrderService") TossOrderService realTossOrderService) {
        
        System.out.println("=================================================");
        System.out.println("  Auto Trading System Mode: " + mode.toUpperCase());
        System.out.println("=================================================");
        
        if ("real".equalsIgnoreCase(mode)) {
            return realTossOrderService;
        }
        return mockTossOrderService;
    }
}
