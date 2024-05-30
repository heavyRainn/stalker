package com.trading.crypto.manager;

import com.trading.crypto.model.RiskEvaluation;
import com.trading.crypto.model.Signal;
import com.trading.crypto.model.Trade;
import com.trading.crypto.model.TradeSignal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Помимо управления рисками, важно включить методы для расчета потенциальной прибыли и убытков, а также уровней стоп-лосс и тейк-профит.
 * Execution (Исполнение)
 */
public interface RiskManager {
    Map<TradeSignal, RiskEvaluation> evaluateRisk(List<TradeSignal> signals, List<Signal> indicatorsAnalysisResult, BigDecimal balance);

    Trade evaluateAndPrepareTrade(TradeSignal signal, RiskEvaluation evaluation, BigDecimal balance);
}
