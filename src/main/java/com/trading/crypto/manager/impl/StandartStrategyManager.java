package com.trading.crypto.manager.impl;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.manager.StrategyManager;
import com.trading.crypto.model.AnalysisResult;
import com.trading.crypto.model.Signal;
import com.trading.crypto.model.TradeSignal;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class StandartStrategyManager implements StrategyManager {
    @Override
    public List<TradeSignal> analyzeData(Map<MarketInterval, Signal> indicatorsAnalysisResult) {
        List<TradeSignal> tradeSignals = new ArrayList<>();

        indicatorsAnalysisResult.forEach((interval, signal) -> {
            AnalysisResult result = signal.getAnalysisResult();
            String symbol = signal.getAsset();
            double entryPrice = signal.getPrice().doubleValue();
            long timestamp = signal.getTimestamp();

            // Предположим фиксированные значения для Stop-Loss, Take-Profit и количества
            double stopLoss = entryPrice * 0.95; // Stop-Loss на уровне 5% ниже цены входа
            double takeProfit = entryPrice * 1.10; // Take-Profit на уровне 10% выше цены входа
            double amount = 1.0; // Количество лотов для входа в сделку

            TradeSignal tradeSignal = new TradeSignal(result, symbol, entryPrice, stopLoss, takeProfit, amount, timestamp);
            tradeSignals.add(tradeSignal);
        });

        return tradeSignals;
    }
}
