package com.trading.crypto.analyzer;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.model.KlineElement;
import com.trading.crypto.model.Signal;

import java.util.List;
import java.util.Map;

public interface Analyser  {
    void update(MarketInterval interval, KlineElement klineElement);
    Map<MarketInterval, Signal> analyze(List<MarketInterval> intervals);
}
