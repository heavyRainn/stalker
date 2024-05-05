package com.trading.crypto.manager.impl;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.manager.StrategyManager;
import com.trading.crypto.model.AnalysisResult;
import com.trading.crypto.model.TradeSignal;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StandartStrategyManager implements StrategyManager {
    @Override
    public TradeSignal analyzeData(Map<MarketInterval, AnalysisResult> indicatorsAnalysisResult) {
        return null;
    }
}
