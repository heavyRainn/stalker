package com.trading.crypto.manager.impl;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.client.BybitClient;
import com.trading.crypto.data.impl.HistoricalDataCollector;
import com.trading.crypto.manager.StrategyManager;
import com.trading.crypto.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
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
        List<PinBarSignal> pinBars = pinBarAnalysisResult.stream().filter(pinBarSignal -> !PinBarAnalysisResult.NO_PIN_BAR.equals(pinBarSignal.getResult())).toList();

        // Обработка сигналов анализа пин-баров
        for (PinBarSignal pinBarSignal : pinBars) {
            String symbol = pinBarSignal.getSymbol();
            double entryPrice = pinBarSignal.getEntryPrice();
            long timestamp = System.currentTimeMillis();
            PinBarAnalysisResult pinBarResult = pinBarSignal.getResult();
            BigDecimal volume = pinBarSignal.getVolume();

            if (!isHighVolume(volume, symbol, pinBarSignal.getInterval())) {
                log.info("Not high volume for pin bar, symbol:{}, volume:{}, interval:{}", symbol, volume, pinBarSignal.getInterval());
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
     * Проверяет, превышает ли текущий объем средний и медианный объемы за все доступные свечи.
     *
     * @param volume   текущий объем.
     * @param symbol   торговый символ.
     * @param interval временной интервал.
     * @return true, если объем превышает медианный и средний объемы за все доступные свечи, иначе false.
     */
    public boolean isHighVolume(BigDecimal volume, String symbol, MarketInterval interval) {
        double volumeThresholdMultiplier = 1.5; // Коэффициент для проверки повышенного объема (50% выше среднего)

        List<KlineElement> klines = dataCollector.getKlineCache().get(symbol).get(interval);

        if (klines == null || klines.isEmpty()) {
            log.warn("Not enough data for symbol: {} and interval: {}", symbol, interval);
            return false; // Если нет данных, возвращаем false
        }

        BigDecimal totalVolume = BigDecimal.ZERO;
        List<BigDecimal> volumes = new ArrayList<>();

        for (KlineElement kline : klines) {
            BigDecimal klineVolume = kline.getVolume();
            totalVolume = totalVolume.add(klineVolume);
            volumes.add(klineVolume);
        }

        int numCandles = klines.size();
        BigDecimal averageVolume = totalVolume.divide(BigDecimal.valueOf(numCandles), BigDecimal.ROUND_HALF_UP);
        BigDecimal thresholdVolume = averageVolume.multiply(BigDecimal.valueOf(volumeThresholdMultiplier));

        Collections.sort(volumes);
        BigDecimal medianVolume = volumes.get(numCandles / 2);

        boolean isHighVolume = volume.compareTo(thresholdVolume) > 0 && volume.compareTo(medianVolume) > 0;

        log.info("Volume check for symbol: {}, interval: {} - Current Volume: {}, Average Volume: {}, Median Volume: {}, Threshold Volume: {}, High Volume: {}",
                symbol, interval, volume, averageVolume, medianVolume, thresholdVolume, isHighVolume);

        return isHighVolume;
    }

}
