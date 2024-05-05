package com.trading.crypto.analyzer;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.model.KlineElement;
import com.trading.crypto.model.Signal;

import java.util.List;

public interface Analyser  {
    void update(MarketInterval interval, KlineElement klineElement);
    List<Signal> getSignalHistory();
}
