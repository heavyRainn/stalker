package com.trading.crypto.order.impl;

import com.bybit.api.client.domain.trade.Side;
import com.trading.crypto.client.BybitClient;
import com.trading.crypto.model.Trade;
import com.trading.crypto.order.OrderExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class OrderExecutorService implements OrderExecutor {

    @Autowired
    private BybitClient bybitClient;

    @Override
    public CompletableFuture<String> executeOrder(Trade trade) {
        CompletableFuture<String> futureOrderId = new CompletableFuture<>();
        try {
            // Получаем текущую цену актива
            BigDecimal currentPrice = bybitClient.getCurrentPrice(trade.getSymbol());
            trade.setEntryPrice(currentPrice.doubleValue());
            adjustStopLossAndTakeProfit(trade);

            // Выставляем лимитную заявку на покупку и получаем ID ордера
            bybitClient.placeLimitOrder(trade).thenAccept(orderId -> {
                if (orderId != null) {
                    log.info("Limit order placed successfully for symbol: {}, orderId:{}", trade.getSymbol(), orderId);
                    futureOrderId.complete(orderId);
                } else {
                    log.error("Failed to place limit order for symbol: {}", trade.getSymbol());
                    futureOrderId.complete(null);
                }
            }).exceptionally(ex -> {
                log.error("Error executing order for symbol: {}", trade.getSymbol(), ex);
                futureOrderId.completeExceptionally(ex);
                return null;
            });
        } catch (Exception e) {
            log.error("Error executing order for symbol: {}", trade.getSymbol(), e);
            futureOrderId.completeExceptionally(e);
        }

        return futureOrderId;
    }

    public void adjustStopLossAndTakeProfit(Trade trade) {
        double entryPrice = trade.getEntryPrice();
        double stopLoss, takeProfit;

        if (trade.getSide() == Side.BUY) {
            stopLoss = entryPrice - entryPrice * 0.005; // Stop-Loss на уровне 0.5% ниже цены входа
            takeProfit = entryPrice + entryPrice * 0.015; // Take-Profit на уровне 1.5% выше цены входа
        } else if (trade.getSide() == Side.SELL) {
            stopLoss = entryPrice + entryPrice * 0.005; // Stop-Loss на уровне 0.5% выше цены входа
            takeProfit = entryPrice - entryPrice * 0.015; // Take-Profit на уровне 1.5% ниже цены входа
        } else {
            // В случае, если side не определен, оставляем стопы без изменений
            return;
        }

        trade.setStopLoss(stopLoss);
        trade.setTakeProfit(takeProfit);

        log.info("Adjusted stop loss and take profit for trade: {}, new Stop Loss: {}, new Take Profit: {}",
                trade, stopLoss, takeProfit);
    }
}

