package com.trading.crypto.model;

import com.bybit.api.client.domain.market.MarketInterval;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.ta4j.core.num.Num;

@Data
@AllArgsConstructor
public class Signal {
    private AnalysisResult analysisResult;
    private String asset;
    private Num price;
    private long timestamp;
    private MarketInterval interval;
}
