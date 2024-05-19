package com.trading.crypto.analyzer.impl;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.model.KlineElement;
import com.trading.crypto.model.PinBarAnalysisResult;
import com.trading.crypto.trader.impl.WaveTrader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PinBarDetector {

    public static Map<MarketInterval, PinBarAnalysisResult> analyze(
            List<MarketInterval> intervals,
            Map<String, Map<MarketInterval, List<KlineElement>>> klineCache) {

        Map<MarketInterval, PinBarAnalysisResult> result = new HashMap<>();

        for (MarketInterval interval : intervals) {
            Map<MarketInterval, List<KlineElement>> intervalMap = klineCache.get(WaveTrader.symbol);
            if (intervalMap != null && intervalMap.containsKey(interval)) {
                List<KlineElement> klines = intervalMap.get(interval);
                if (klines != null && !klines.isEmpty()) {
                    KlineElement lastKline = klines.get(klines.size() - 1);
                    if (lastKline.isBullishPinBar()) {
                        result.put(interval, PinBarAnalysisResult.BULLISH_PIN_BAR);
                    } else if (lastKline.isBearishPinBar()) {
                        result.put(interval, PinBarAnalysisResult.BEARISH_PIN_BAR);
                    } else {
                        result.put(interval, PinBarAnalysisResult.NO_PIN_BAR);
                    }
                } else {
                    result.put(interval, PinBarAnalysisResult.NO_PIN_BAR);
                }
            } else {
                result.put(interval, PinBarAnalysisResult.NO_PIN_BAR);
            }
        }

        return result;
    }
}
