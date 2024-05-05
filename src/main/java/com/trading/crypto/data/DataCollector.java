package com.trading.crypto.data;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.model.KlineElement;

import java.util.List;
import java.util.Map;

public interface DataCollector {
    Map<String, Map<MarketInterval, List<KlineElement>>> getKlineCache();
}
