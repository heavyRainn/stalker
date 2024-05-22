package com.trading.crypto.manager.impl;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.data.impl.HistoricalDataCollector;
import com.trading.crypto.manager.StrategyManager;
import com.trading.crypto.model.*;
import lombok.experimental.Accessors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.ta4j.core.TimeSeries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class StandartStrategyManager implements StrategyManager {
    private static final double INITIAL_BALANCE = 100.0; // Начальный баланс
    private double balance = INITIAL_BALANCE; // Текущий баланс

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
            double riskPerTrade = balance * 0.02; // Риск на сделку 2% от текущего баланса
            double amount;

            if (result == AnalysisResult.BUY || result == AnalysisResult.STRONG_BUY) {
                stopLoss = entryPrice * 0.95; // Stop-Loss на уровне 5% ниже цены входа
                takeProfit = entryPrice * 1.10; // Take-Profit на уровне 10% выше цены входа
                amount = riskPerTrade / (entryPrice - stopLoss);
            } else if (result == AnalysisResult.SELL || result == AnalysisResult.STRONG_SELL) {
                stopLoss = entryPrice * 1.05; // Stop-Loss на уровне 5% выше цены входа
                takeProfit = entryPrice * 0.90; // Take-Profit на уровне 10% ниже цены входа
                amount = riskPerTrade / (stopLoss - entryPrice);
            } else {
                continue;
            }

            TradeSignal tradeSignal = new TradeSignal(result, symbol, entryPrice, stopLoss, takeProfit, amount, timestamp);
            tradeSignals.add(tradeSignal);
        }

        // Обработка сигналов анализа пин-баров
        for (PinBarSignal pinBarSignal : pinBarAnalysisResult) {
            String symbol = pinBarSignal.getSymbol();
            MarketInterval interval = pinBarSignal.getInterval();
            double entryPrice = pinBarSignal.getEntryPrice(); // Получить последнюю цену для символа и интервала
            long timestamp = System.currentTimeMillis();
            PinBarAnalysisResult pinBarResult = pinBarSignal.getResult();

            double stopLoss, takeProfit;
            double riskPerTrade = balance * 0.02; // Риск на сделку 2% от текущего баланса
            double amount;

            AnalysisResult result;
            if (pinBarResult == PinBarAnalysisResult.BULLISH_PIN_BAR) {
                result = AnalysisResult.BUY;
                stopLoss = entryPrice * 0.95; // Stop-Loss на уровне 5% ниже цены входа
                takeProfit = entryPrice * 1.10; // Take-Profit на уровне 10% выше цены входа
                amount = riskPerTrade / (entryPrice - stopLoss);
            } else if (pinBarResult == PinBarAnalysisResult.BEARISH_PIN_BAR) {
                result = AnalysisResult.SELL;
                stopLoss = entryPrice * 1.05; // Stop-Loss на уровне 5% выше цены входа
                takeProfit = entryPrice * 0.90; // Take-Profit на уровне 10% ниже цены входа
                amount = riskPerTrade / (stopLoss - entryPrice);
            } else {
                continue;
            }

            TradeSignal tradeSignal = new TradeSignal(result, symbol, entryPrice, stopLoss, takeProfit, amount, timestamp);
            tradeSignals.add(tradeSignal);
        }

        return tradeSignals;
    }

    private AnalysisResult combineAnalysisResults(AnalysisResult indicatorResult, PinBarAnalysisResult pinBarResult) {
        if (indicatorResult == AnalysisResult.HOLD) {
            return AnalysisResult.HOLD;
        }

        if ((indicatorResult == AnalysisResult.BUY || indicatorResult == AnalysisResult.STRONG_BUY) &&
                pinBarResult == PinBarAnalysisResult.BULLISH_PIN_BAR) {
            return AnalysisResult.BUY;
        }

        if ((indicatorResult == AnalysisResult.SELL || indicatorResult == AnalysisResult.STRONG_SELL) &&
                pinBarResult == PinBarAnalysisResult.BEARISH_PIN_BAR) {
            return AnalysisResult.SELL;
        }

        return AnalysisResult.HOLD;
    }
}
