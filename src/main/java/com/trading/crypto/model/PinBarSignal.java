package com.trading.crypto.model;

import com.bybit.api.client.domain.market.MarketInterval;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class PinBarSignal {
    private String symbol;
    private double entryPrice;
    private MarketInterval interval;
    private PinBarAnalysisResult result;
    private BigDecimal volume;
}
