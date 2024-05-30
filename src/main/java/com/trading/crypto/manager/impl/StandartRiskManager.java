package com.trading.crypto.manager.impl;

import com.trading.crypto.client.BybitClient;
import com.trading.crypto.manager.RiskManager;
import com.trading.crypto.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * StandartRiskManager - реализация интерфейса RiskManager.
 * Управляет рисками для торговых сигналов, используя баланс с Bybit.
 */
@Slf4j
@Component
public class StandartRiskManager implements RiskManager {
    private static final double RISK_PER_TRADE = 0.01; // Рисковать 1% баланса на сделку

    @Autowired
    private BybitClient bybitClient;

    /**
     * Оценивает риск для каждого торгового сигнала.
     *
     * @param signals                  список торговых сигналов.
     * @param indicatorsAnalysisResult результаты анализа индикаторов.
     * @param balance
     * @return карта торговых сигналов и их оценок риска.
     */
    @Override
    public Map<TradeSignal, RiskEvaluation> evaluateRisk(List<TradeSignal> signals, List<Signal> indicatorsAnalysisResult, BigDecimal balance) {
        Map<TradeSignal, RiskEvaluation> riskEvaluations = new HashMap<>();

        if (balance.compareTo(BigDecimal.valueOf(5)) < 0) {
            log.info("No enough money on balance: {}", balance);
            return riskEvaluations;
        }

        // Оцениваем риск для каждого сигнала
        for (TradeSignal signal : signals) {
            RiskEvaluation evaluation = RiskEvaluation.ACCEPTABLE;
            double amount = calculateTradeAmount(balance.doubleValue(), signal.getEntryPrice());

            signal.setAmount(amount);

            // Пример оценки риска на основе условных параметров
            switch (signal.getSignalType()) {
                case STRONG_BUY, BUY -> {
                    // Низкий риск для сильных покупок, средний - для обычных
                }
                case STRONG_SELL, SELL -> {
                    // Средний риск для продаж, высокий - для сильных продаж
                    evaluation = signal.getSignalType() == AnalysisResult.STRONG_SELL ? RiskEvaluation.HIGH : RiskEvaluation.MEDIUM;
                }
                case HOLD ->
                    // Минимальный риск, но торговля не рекомендуется
                        evaluation = RiskEvaluation.LOW;
            }
            riskEvaluations.put(signal, evaluation);
        }

        return riskEvaluations;
    }

    /**
     * Подготавливает торговую сделку на основе торгового сигнала и оценки риска.
     *
     * @param signal     торговый сигнал.
     * @param evaluation оценка риска.
     * @param balance
     * @return объект Trade, представляющий сделку.
     */
    @Override
    public Trade evaluateAndPrepareTrade(TradeSignal signal, RiskEvaluation evaluation, BigDecimal balance) {
        double amount = calculateTradeAmount(balance.doubleValue(), signal.getEntryPrice());

        // Рассчитываем Stop-Loss и Take-Profit на основе процентных значений
        double stopLoss = signal.getEntryPrice() - signal.getEntryPrice() * 0.005;
        double takeProfit = signal.getEntryPrice() + signal.getEntryPrice() * 0.015;

        return new Trade(signal.getSymbol(), signal.getEntryPrice(), stopLoss, takeProfit, amount);
    }

    /**
     * Рассчитывает количество лотов для торговли на основе баланса и цены входа.
     *
     * @param balance текущий баланс.
     * @param entryPrice цена входа.
     * @return количество лотов для торговли.
     */
    private double calculateTradeAmount(double balance, double entryPrice) {
        // Входим всем депозитом минус 3%
        double riskBalance = balance * 0.97;
        double riskAmount = riskBalance * RISK_PER_TRADE;
        return riskAmount / entryPrice;
    }
}
