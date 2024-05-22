package com.trading.crypto.model;

import com.bybit.api.client.domain.market.MarketInterval;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PinBarSignal {
    private String symbol;
    private double entryPrice;
    private MarketInterval interval;
    private PinBarAnalysisResult result;
}
