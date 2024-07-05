package com.trading.crypto.manager.impl;

import com.bybit.api.client.domain.trade.Side;
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

    @Autowired
    private BybitClient bybitClient;

    /**
     * Оценивает риск для каждого торгового сигнала.
     *
     * @param signals                  список торговых сигналов.
     * @param indicatorsAnalysisResult результаты анализа индикаторов.
     * @param balance                  текущий баланс.
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
            double amount = calculateTradeAmount(signal.getSymbol(), balance.doubleValue(), signal.getEntryPrice());

            signal.setAmount(amount);

            // Пример оценки риска на основе условных параметров
            switch (signal.getSignalType()) {
                case STRONG_BUY, BUY -> {
                    // Низкий риск для сильных покупок, средний - для обычных
                }
                case STRONG_SELL, SELL -> {
                    // Средний риск для продаж, высокий - для сильных продаж
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
     * @param balance    текущий баланс.
     * @return объект Trade, представляющий сделку.
     */
    @Override
    public Trade evaluateAndPrepareTrade(TradeSignal signal, RiskEvaluation evaluation, BigDecimal balance) {
        double amount = calculateTradeAmount(signal.getSymbol(), balance.doubleValue(), signal.getEntryPrice());

        double stopLoss, takeProfit;

        if (signal.getSignalType() == AnalysisResult.BUY || signal.getSignalType() == AnalysisResult.STRONG_BUY) {
            stopLoss = signal.getEntryPrice() - signal.getEntryPrice() * 0.005; // Stop-Loss на уровне 0.5% ниже цены входа
            takeProfit = signal.getEntryPrice() + signal.getEntryPrice() * 0.015; // Take-Profit на уровне 1.5% выше цены входа
        } else if (signal.getSignalType() == AnalysisResult.SELL || signal.getSignalType() == AnalysisResult.STRONG_SELL) {
            stopLoss = signal.getEntryPrice() + signal.getEntryPrice() * 0.005; // Stop-Loss на уровне 0.5% выше цены входа
            takeProfit = signal.getEntryPrice() - signal.getEntryPrice() * 0.015; // Take-Profit на уровне 1.5% ниже цены входа
        } else {
            throw new IllegalArgumentException("Invalid signal type: " + signal.getSignalType());
        }

        return new Trade(
                signal.getSymbol(),
                roundToThreeDecimalPlaces(signal.getEntryPrice()),
                roundToThreeDecimalPlaces(stopLoss),
                roundToThreeDecimalPlaces(takeProfit),
                roundToThreeDecimalPlaces(amount),
                getSide(signal.getSignalType())
        );
    }

    /**
     * Округляет значение до 3-х знаков после запятой.
     *
     * @param value значение для округления.
     * @return округленное значение.
     */
    private double roundToThreeDecimalPlaces(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private Side getSide(AnalysisResult signalType) {
        return switch (signalType) {
            case STRONG_BUY, BUY -> Side.BUY;
            case STRONG_SELL, SELL -> Side.SELL;
            default -> throw new IllegalArgumentException("Invalid signal type: " + signalType);
        };
    }

    /**
     * Рассчитывает количество лотов для торговли на основе баланса и цены входа.
     *
     * @param symbol символ валютной пары.
     * @param balance текущий баланс.
     * @param entryPrice цена входа.
     * @return количество лотов для торговли.
     */
    private double calculateTradeAmount(String symbol, double balance, double entryPrice) {
        // Входим 90% от текущего баланса
        double riskBalance = balance * 0.9;
        double amount = riskBalance / entryPrice;

        if ("ADAUSDT".equals(symbol)) {
            amount = Math.floor(amount); // Округление до целого числа в меньшую сторону
        }

        return amount;
    }
}
