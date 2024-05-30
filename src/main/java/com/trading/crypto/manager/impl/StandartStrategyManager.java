package com.trading.crypto.manager.impl;

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
        List<TradeSignal> tradeSignals = new ArrayList<>();// Получаем текущий баланс

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

        // Обработка сигналов анализа пин-баров
        for (PinBarSignal pinBarSignal : pinBarAnalysisResult) {
            String symbol = pinBarSignal.getSymbol();
            double entryPrice = pinBarSignal.getEntryPrice(); // Получить последнюю цену для символа и интервала
            long timestamp = System.currentTimeMillis();
            PinBarAnalysisResult pinBarResult = pinBarSignal.getResult();

            double stopLoss, takeProfit;
            double amount;

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

}
