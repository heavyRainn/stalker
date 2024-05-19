package com.trading.crypto.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * https://bybit-exchange.github.io/docs/v5/market/mark-kline#http-request
 */
@Data
@AllArgsConstructor
public class KlineElement {
    private Long timestamp;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private BigDecimal volume;
    private BigDecimal turnover;

    public boolean isBullishPinBar() {
        BigDecimal body = openPrice.subtract(closePrice).abs();
        BigDecimal upperShadow = highPrice.subtract(openPrice.max(closePrice));
        BigDecimal lowerShadow = openPrice.min(closePrice).subtract(lowPrice);

        // Условие для определения бычьего пин-бара
        return lowerShadow.compareTo(body.multiply(BigDecimal.valueOf(2))) > 0 &&
                upperShadow.compareTo(body.multiply(BigDecimal.valueOf(0.5))) < 0;
    }

    public boolean isBearishPinBar() {
        BigDecimal body = openPrice.subtract(closePrice).abs();
        BigDecimal upperShadow = highPrice.subtract(openPrice.max(closePrice));
        BigDecimal lowerShadow = openPrice.min(closePrice).subtract(lowPrice);

        // Условие для определения медвежьего пин-бара
        return upperShadow.compareTo(body.multiply(BigDecimal.valueOf(2))) > 0 &&
                lowerShadow.compareTo(body.multiply(BigDecimal.valueOf(0.5))) < 0;
    }
}
