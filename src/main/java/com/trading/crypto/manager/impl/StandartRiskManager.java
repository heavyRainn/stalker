package com.trading.crypto.manager.impl;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.model.AnalysisResult;
import com.trading.crypto.model.RiskEvaluation;
import com.trading.crypto.manager.RiskManager;
import com.trading.crypto.model.Trade;
import com.trading.crypto.model.TradeSignal;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class StandartRiskManager implements RiskManager {
    private static final double RISK_PER_TRADE = 0.01; // Рисковать 1% баланса на сделку

    @Override
    public RiskEvaluation evaluateRisk(List<TradeSignal> signal, Map<MarketInterval, AnalysisResult> indicatorsAnalysisResult) {
        return RiskEvaluation.HIGH;
    }

    @Override
    public Trade evaluateAndPrepareTrade(double entryPrice, double accountBalance, double volatility) {
        double riskAmount = accountBalance * RISK_PER_TRADE;
        double amount = riskAmount / entryPrice; // Размер позиции

        // Рассчитываем Stop-Loss и Take-Profit на основе волатильности
        double stopLoss = entryPrice - volatility * 2; // Пример: Stop-Loss на 2x волатильности
        double takeProfit = entryPrice + volatility * 6; // Пример: Take-Profit на 6x волатильности

        return new Trade(entryPrice, stopLoss, takeProfit, amount);
    }
}
