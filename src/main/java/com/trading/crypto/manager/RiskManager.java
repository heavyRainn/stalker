package com.trading.crypto.manager;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.model.*;

import java.util.List;
import java.util.Map;

/**
 * Помимо управления рисками, важно включить методы для расчета потенциальной прибыли и убытков, а также уровней стоп-лосс и тейк-профит.
 * Execution (Исполнение)
 */
public interface RiskManager {
    Map<TradeSignal, RiskEvaluation> evaluateRisk(List<TradeSignal> signals, Map<MarketInterval, Signal> indicatorsAnalysisResult);

    Trade evaluateAndPrepareTrade(TradeSignal signal, RiskEvaluation evaluation);
}
