package com.trading.crypto.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

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

    public boolean isBullishPinBar(KlineElement previousCandle) {
        BigDecimal body = closePrice.subtract(openPrice).abs();
        BigDecimal upperShadow = highPrice.subtract(closePrice.max(openPrice));
        BigDecimal lowerShadow = openPrice.min(closePrice).subtract(lowPrice);

        // Условия для бычьего пин-бара
        return previousCandle.isBearish() &&
                lowerShadow.compareTo(body.multiply(BigDecimal.valueOf(2))) > 0 &&
                upperShadow.compareTo(body.multiply(BigDecimal.valueOf(0.5))) < 0 &&
                closePrice.compareTo(openPrice) > 0;
    }

    public boolean isBearishPinBar(KlineElement previousCandle) {
        BigDecimal body = openPrice.subtract(closePrice).abs();
        BigDecimal upperShadow = highPrice.subtract(openPrice.max(closePrice));
        BigDecimal lowerShadow = openPrice.min(closePrice).subtract(lowPrice);

        // Условия для медвежьего пин-бара
        return previousCandle.isBullish() &&
                upperShadow.compareTo(body.multiply(BigDecimal.valueOf(2))) > 0 &&
                lowerShadow.compareTo(body.multiply(BigDecimal.valueOf(0.5))) < 0 &&
                closePrice.compareTo(openPrice) < 0;
    }

    private boolean isBullish() {
        return closePrice.compareTo(openPrice) > 0;
    }

    private boolean isBearish() {
        return closePrice.compareTo(openPrice) < 0;
    }
}
