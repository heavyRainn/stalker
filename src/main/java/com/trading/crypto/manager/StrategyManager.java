package com.trading.crypto.manager;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.model.Signal;
import com.trading.crypto.model.TradeSignal;

import java.util.List;
import java.util.Map;

public interface StrategyManager {

    List<TradeSignal> analyzeData(Map<MarketInterval, Signal> indicatorsAnalysisResult);
}
