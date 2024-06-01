package com.trading.crypto.analyzer.impl;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.model.KlineElement;
import com.trading.crypto.model.PinBarAnalysisResult;
import com.trading.crypto.model.PinBarSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PinBarDetector {

    public static List<PinBarSignal> analyze(List<String> symbols, List<MarketInterval> intervals, Map<String, Map<MarketInterval, List<KlineElement>>> klineCache) {
        List<PinBarSignal> signals = new ArrayList<>();

        for (String symbol : symbols) {
            Map<MarketInterval, List<KlineElement>> intervalMap = klineCache.get(symbol);

            if (intervalMap != null) {
                for (MarketInterval interval : intervals) {
                    List<KlineElement> klines = intervalMap.get(interval);
                    if (klines != null && !klines.isEmpty()) {
                        PinBarAnalysisResult result = getPinBarAnalysisResult(klines);
                        KlineElement klineElement = klines.get(0);
                        signals.add(new PinBarSignal(symbol, klineElement.getClosePrice().doubleValue(), interval, result, klineElement.getVolume()));
                    }
                }
            }
        }

        return signals;
    }

    private static PinBarAnalysisResult getPinBarAnalysisResult(List<KlineElement> klines) {
        KlineElement firstKline = klines.get(0); // Первый элемент в списке
        PinBarAnalysisResult result;
        if (firstKline.isBullishPinBar(klines.get(1))) {
            result = PinBarAnalysisResult.BULLISH_PIN_BAR;
        } else if (firstKline.isBearishPinBar(klines.get(1))) {
            result = PinBarAnalysisResult.BEARISH_PIN_BAR;
        } else {
            result = PinBarAnalysisResult.NO_PIN_BAR;
        }
        return result;
    }
}