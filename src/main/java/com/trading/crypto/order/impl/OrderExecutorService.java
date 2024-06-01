package com.trading.crypto.order.impl;

import com.trading.crypto.client.BybitClient;
import com.trading.crypto.model.Trade;
import com.trading.crypto.order.OrderExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class OrderExecutorService implements OrderExecutor {

    @Autowired
    private BybitClient bybitClient;

    @Override
    public void executeOrder(Trade trade) {
        try {
            // Получаем текущую цену актива
            BigDecimal currentPrice = bybitClient.getCurrentPrice(trade.getSymbol());
            trade.setEntryPrice(currentPrice.doubleValue());

            // Выставляем лимитную заявку на покупку
            boolean orderResult = bybitClient.placeLimitOrder(trade);
            if (orderResult) {
                log.info("Limit order placed successfully for symbol: {}", trade.getSymbol());
            } else {
                log.error("Failed to place limit order for symbol: {}", trade.getSymbol());
            }
        } catch (Exception e) {
            log.error("Error executing order for symbol: {}", trade.getSymbol(), e);
        }
    }
}

