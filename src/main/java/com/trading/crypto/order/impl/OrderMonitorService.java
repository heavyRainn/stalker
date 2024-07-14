package com.trading.crypto.order.impl;

import com.trading.crypto.client.BybitClient;
import com.trading.crypto.model.Trade;
import com.trading.crypto.util.LogUtils;
import com.trading.crypto.util.StalkerUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для мониторинга активных ордеров и обновления Stop Loss и Take Profit.
 */
@Slf4j
@Service
public class OrderMonitorService {
    private final BybitClient bybitClient;

    public OrderMonitorService(BybitClient bybitClient) {
        this.bybitClient = bybitClient;
    }

    /**
     * Мониторинг активных ордеров и обновление их Stop Loss и Take Profit.
     *
     * @param activeOrders список активных ордеров.
     */
    public void monitorOrders(List<Trade> activeOrders) {
        List<Trade> ordersToRemove = new ArrayList<>();

        for (Trade trade : activeOrders) {
            BigDecimal currentPrice = bybitClient.getCurrentPrice(trade.getSymbol());
            BigDecimal unrealizedPnl = calculateUnrealizedPnl(trade, currentPrice);
            double pnlPercentage = StalkerUtils.calculatePnLPercentage(unrealizedPnl, BigDecimal.valueOf(trade.getEntryPrice()), BigDecimal.valueOf(trade.getAmount()));
            LogUtils.logActiveTrade(trade, pnlPercentage);

            if (pnlPercentage > 0.5) {
                updateStopLossAndTakeProfit(trade, currentPrice);
            }

            if (isOrderExecuted(trade)) {
                ordersToRemove.add(trade);
            }
        }

        activeOrders.removeAll(ordersToRemove);
    }

    private BigDecimal calculateUnrealizedPnl(Trade trade, BigDecimal currentPrice) {
        BigDecimal entryPrice = BigDecimal.valueOf(trade.getEntryPrice());
        BigDecimal amount = BigDecimal.valueOf(trade.getAmount());
        return currentPrice.subtract(entryPrice).multiply(amount);
    }

    private void updateStopLossAndTakeProfit(Trade trade, BigDecimal currentPrice) {
        BigDecimal newStopLoss = currentPrice.subtract(BigDecimal.valueOf(0.01));
        BigDecimal newTakeProfit = currentPrice.add(BigDecimal.valueOf(0.02));

        trade.setStopLoss(newStopLoss.doubleValue());
        trade.setTakeProfit(newTakeProfit.doubleValue());

        //bybitClient.updateOrder(trade); // Обновление ордера через bybitClient
    }

    private boolean isOrderExecuted(Trade trade) {
        // Проверка, исполнен ли ордер. Логика проверки зависит от реализации bybitClient.
        // Например:
        // return bybitClient.isOrderExecuted(trade.getOrderId());
        return false;
    }
}


