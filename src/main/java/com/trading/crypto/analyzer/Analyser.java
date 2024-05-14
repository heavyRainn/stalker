package com.trading.crypto.analyzer;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.model.KlineElement;

public interface Analyser  {
    void update(MarketInterval interval, KlineElement klineElement);
}
