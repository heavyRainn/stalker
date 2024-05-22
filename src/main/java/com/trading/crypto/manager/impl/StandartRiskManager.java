package com.trading.crypto.manager.impl;

import com.trading.crypto.manager.RiskManager;
import com.trading.crypto.model.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class StandartRiskManager implements RiskManager {
    private static final double RISK_PER_TRADE = 0.01; // Рисковать 1% баланса на сделку

    @Override
    public Map<TradeSignal, RiskEvaluation> evaluateRisk(List<TradeSignal> signals, List<Signal> indicatorsAnalysisResult) {
        Map<TradeSignal, RiskEvaluation> riskEvaluations = new HashMap<>();

        for (TradeSignal signal : signals) {
            RiskEvaluation evaluation = RiskEvaluation.ACCEPTABLE;
            // Пример оценки риска на основе условных параметров
            switch (signal.getSignalType()) {
                case STRONG_BUY -> {
                    // Низкий риск для сильных покупок, средний - для обычных
                    evaluation = RiskEvaluation.LOW;
                    signal.setAmount(calculateLotSize(signal, RiskEvaluation.LOW));
                }
                case BUY -> {
                    // Низкий риск для сильных покупок, средний - для обычных
                    evaluation = RiskEvaluation.MEDIUM;
                    signal.setAmount(calculateLotSize(signal, RiskEvaluation.MEDIUM));
                }
                case STRONG_SELL -> {
                    // Средний риск для продаж, высокий - для сильных продаж
                    evaluation = signal.getSignalType() == AnalysisResult.STRONG_SELL ? RiskEvaluation.HIGH : RiskEvaluation.MEDIUM;
                    signal.setAmount(calculateLotSize(signal, RiskEvaluation.TOO_HIGH));
                }
                case SELL -> {
                    // Средний риск для продаж, высокий - для сильных продаж
                    evaluation = signal.getSignalType() == AnalysisResult.STRONG_SELL ? RiskEvaluation.HIGH : RiskEvaluation.MEDIUM;
                    signal.setAmount(calculateLotSize(signal, RiskEvaluation.HIGH));
                }
                case HOLD ->
                    // Минимальный риск, но торговля не рекомендуется
                        evaluation = RiskEvaluation.LOW;
            }
            riskEvaluations.put(signal, evaluation);
        }

        return riskEvaluations;
    }

    @Override
    public Trade evaluateAndPrepareTrade(TradeSignal signal, RiskEvaluation evaluation) {
        double riskAmount = 100 * RISK_PER_TRADE;
        double amount = riskAmount / signal.getEntryPrice(); // Размер позиции

        // Рассчитываем Stop-Loss и Take-Profit на основе волатильности
        double stopLoss = signal.getEntryPrice() - signal.getEntryPrice() * 0.005;
        double takeProfit = signal.getEntryPrice() + signal.getEntryPrice() * 0.015;


        return new Trade(signal.getSymbol(), signal.getEntryPrice(), stopLoss, takeProfit, amount);
    }

    private double calculateLotSize(TradeSignal signal, RiskEvaluation riskLevel) {
        // Примерный расчет размера лота в зависимости от уровня риска
        double baseLotSize = 1.0; // базовый размер лота
        return switch (riskLevel) {
            case LOW -> baseLotSize * 1.5;
            case MEDIUM -> baseLotSize;
            case HIGH -> baseLotSize * 0.5;
            default -> 0;
        };
    }

}
