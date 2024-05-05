package com.trading.crypto.manager;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.model.AnalysisResult;
import com.trading.crypto.model.TradeSignal;

import java.util.Map;

public interface StrategyManager {

    TradeSignal analyzeData(Map<MarketInterval, AnalysisResult> indicatorsAnalysisResult);
}
