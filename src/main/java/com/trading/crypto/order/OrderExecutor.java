package com.trading.crypto.order;

import com.trading.crypto.model.Trade;

import java.util.concurrent.CompletableFuture;

public interface OrderExecutor {
    CompletableFuture<String> executeOrder(Trade trade);
}
