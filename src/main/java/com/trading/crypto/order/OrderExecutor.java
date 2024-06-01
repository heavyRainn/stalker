package com.trading.crypto.order;

import com.trading.crypto.model.Trade;

public interface OrderExecutor {
    void executeOrder(Trade trade);
}
