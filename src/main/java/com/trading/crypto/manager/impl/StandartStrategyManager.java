package com.trading.crypto.manager.impl;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.client.BybitClient;
import com.trading.crypto.data.impl.HistoricalDataCollector;
import com.trading.crypto.manager.StrategyManager;
import com.trading.crypto.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class StandartStrategyManager implements StrategyManager {

    @Autowired
    private BybitClient bybitClient;

    @Autowired
    private HistoricalDataCollector dataCollector;

    @Override
    public List<TradeSignal> analyzeData(List<Signal> indicatorsAnalysisResult, List<PinBarSignal> pinBarAnalysisResult) {
        List<TradeSignal> tradeSignals = new ArrayList<>();

        // Обработка сигналов анализа индикаторов
        for (Signal signal : indicatorsAnalysisResult) {
            String symbol = signal.getAsset();
            double entryPrice = signal.getPrice().doubleValue();
            long timestamp = signal.getTimestamp();
            AnalysisResult result = signal.getAnalysisResult();

            double stopLoss, takeProfit;

            if (result == AnalysisResult.BUY || result == AnalysisResult.STRONG_BUY) {
                stopLoss = entryPrice - entryPrice * 0.005; // Stop-Loss на уровне 0.5% ниже цены входа
                takeProfit = entryPrice + entryPrice * 0.015; // Take-Profit на уровне 1.5% выше цены входа
            } else if (result == AnalysisResult.SELL || result == AnalysisResult.STRONG_SELL) {
                stopLoss = entryPrice + entryPrice * 0.005; // Stop-Loss на уровне 0.5% выше цены входа
                takeProfit = entryPrice - entryPrice * 0.015; // Take-Profit на уровне 1.5% ниже цены входа
            } else {
                continue;
            }

            TradeSignal tradeSignal = new TradeSignal(result, symbol, entryPrice, stopLoss, takeProfit, SignalOrigin.INDICATORS, timestamp);
            tradeSignals.add(tradeSignal);
        }

        if (pinBarAnalysisResult == null) return tradeSignals;

        // Обработка сигналов анализа пин-баров
        for (PinBarSignal pinBarSignal : pinBarAnalysisResult) {
            String symbol = pinBarSignal.getSymbol();
            double entryPrice = pinBarSignal.getEntryPrice();
            long timestamp = System.currentTimeMillis();
            PinBarAnalysisResult pinBarResult = pinBarSignal.getResult();
            BigDecimal volume = pinBarSignal.getVolume();

            if (!isHighVolume(volume, symbol, pinBarSignal.getInterval())) {
                continue;
            }

            double stopLoss, takeProfit;
            AnalysisResult result;
            if (pinBarResult == PinBarAnalysisResult.BULLISH_PIN_BAR) {
                result = AnalysisResult.BUY;
                stopLoss = entryPrice - entryPrice * 0.005; // Stop-Loss на уровне 0.5% ниже цены входа
                takeProfit = entryPrice + entryPrice * 0.015; // Take-Profit на уровне 1.5% выше цены входа
            } else if (pinBarResult == PinBarAnalysisResult.BEARISH_PIN_BAR) {
                result = AnalysisResult.SELL;
                stopLoss = entryPrice + entryPrice * 0.005; // Stop-Loss на уровне 0.5% выше цены входа
                takeProfit = entryPrice - entryPrice * 0.015; // Take-Profit на уровне 1.5% ниже цены входа
            } else {
                continue;
            }

            TradeSignal tradeSignal = new TradeSignal(result, symbol, entryPrice, stopLoss, takeProfit, SignalOrigin.PIN_BAR, timestamp);
            tradeSignals.add(tradeSignal);
        }

        return tradeSignals;
    }

    /**
     * Проверяет, превышает ли текущий объем средний объем за последние n свечей.
     *
     * @param volume   текущий объем.
     * @param symbol   торговый символ.
     * @param interval временной интервал.
     * @return true, если объем превышает средний объем за последние n свечей, иначе false.
     */
    public boolean isHighVolume(BigDecimal volume, String symbol, MarketInterval interval) {
        List<KlineElement> recentKlines = dataCollector.getKlineCache().get(symbol).get(interval).subList(0, 15);

        if (recentKlines.isEmpty()) {
            return false; // Если нет данных, возвращаем false
        }

        BigDecimal totalVolume = BigDecimal.ZERO;
        for (KlineElement kline : recentKlines) {
            totalVolume = totalVolume.add(kline.getVolume());
        }

        BigDecimal averageVolume = totalVolume.divide(BigDecimal.valueOf(recentKlines.size()), BigDecimal.ROUND_HALF_UP);
        return volume.compareTo(averageVolume) > 0;
    }

}
