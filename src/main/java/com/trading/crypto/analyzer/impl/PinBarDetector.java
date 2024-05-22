package com.trading.crypto.analyzer.impl;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.model.KlineElement;
import com.trading.crypto.model.PinBarAnalysisResult;
import com.trading.crypto.model.PinBarSignal;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PinBarDetector {

    public static List<PinBarSignal> analyze(List<String> symbols, List<MarketInterval> intervals,
                                             Map<String, Map<MarketInterval, List<KlineElement>>> klineCache) {

        List<PinBarSignal> signals = new ArrayList<>();

        for (String symbol : symbols) {
            Map<MarketInterval, List<KlineElement>> intervalMap = klineCache.get(symbol);

            if (intervalMap != null) {
                for (MarketInterval interval : intervals) {
                    List<KlineElement> klines = intervalMap.get(interval);
                    if (klines != null && !klines.isEmpty()) {
                        PinBarAnalysisResult result = getPinBarAnalysisResult(klines);
                        signals.add(new PinBarSignal(symbol, klines.get(0).getClosePrice().doubleValue(), interval, result));
                    } else {
                        signals.add(new PinBarSignal(symbol, 0, interval, PinBarAnalysisResult.NO_PIN_BAR));
                    }
                }
            }
        }

        return signals;
    }

    private static @NotNull PinBarAnalysisResult getPinBarAnalysisResult(List<KlineElement> klines) {
        KlineElement lastKline = klines.get(klines.size() - 1);
        PinBarAnalysisResult result;
        if (lastKline.isBullishPinBar()) {
            result = PinBarAnalysisResult.BULLISH_PIN_BAR;
        } else if (lastKline.isBearishPinBar()) {
            result = PinBarAnalysisResult.BEARISH_PIN_BAR;
        } else {
            result = PinBarAnalysisResult.NO_PIN_BAR;
        }
        return result;
    }
}