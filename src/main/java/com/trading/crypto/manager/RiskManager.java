package com.trading.crypto.manager;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.model.AnalysisResult;
import com.trading.crypto.model.RiskEvaluation;
import com.trading.crypto.model.Trade;
import com.trading.crypto.model.TradeSignal;

import java.util.List;
import java.util.Map;

/**
 *  Помимо управления рисками, важно включить методы для расчета потенциальной прибыли и убытков, а также уровней стоп-лосс и тейк-профит.
 * Execution (Исполнение)
 */
public interface RiskManager {
    RiskEvaluation evaluateRisk(List<TradeSignal> signals, Map<MarketInterval, AnalysisResult> indicatorsAnalysisResult);
    Trade evaluateAndPrepareTrade(double entryPrice, double accountBalance, double volatility);
}
