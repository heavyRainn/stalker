package com.trading.crypto.order.impl;

import com.bybit.api.client.domain.trade.Side;
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
 * Сервис для мониторинга активных ордеров и обновления уровней Stop Loss и Take Profit.
 * Этот сервис отслеживает активные сделки и обновляет стоп-лосс и тейк-профит
 * в зависимости от текущей рыночной цены.
 */
@Slf4j
@Service
public class OrderMonitorService {
    private final BybitClient bybitClient;

    /**
     * Конструктор для инициализации OrderMonitorService с использованием BybitClient.
     *
     * @param bybitClient Клиент, используемый для взаимодействия с API Bybit.
     */
    public OrderMonitorService(BybitClient bybitClient) {
        this.bybitClient = bybitClient;
    }

    /**
     * Мониторинг активных ордеров и обновление их уровней Stop Loss и Take Profit.
     * Удаляет ордера из списка, если они исполнены.
     *
     * @param activeOrders Список активных ордеров.
     */
    public void monitorOrders(List<Trade> activeOrders) {
        // Список ордеров, которые будут удалены после проверки
        List<Trade> ordersToRemove = new ArrayList<>();

        // Проходим по каждому активному ордеру для мониторинга и управления его состоянием
        for (Trade trade : activeOrders) {
            // Получаем текущую рыночную цену для символа ордера
            BigDecimal currentPrice = bybitClient.getCurrentPrice(trade.getSymbol());

            // Рассчитываем нереализованную прибыль или убыток на основе текущей цены
            BigDecimal unrealizedPnl = calculateUnrealizedPnl(trade, currentPrice);

            // Рассчитываем процент прибыли или убытка для текущей сделки
            double pnlPercentage = StalkerUtils.calculatePnLPercentage(
                    unrealizedPnl,
                    BigDecimal.valueOf(trade.getEntryPrice()),
                    BigDecimal.valueOf(trade.getAmount())
            );

            // Логируем информацию о текущей сделке и процент прибыли
            LogUtils.logActiveTrade(trade, pnlPercentage);

            // Проверяем, превышает ли процент прибыли заданный порог (0.5%)
            if (pnlPercentage > 0.5) {
                // Если да, обновляем уровни стоп-лосс и тейк-профит для данной сделки
                //updateStopLossAndTakeProfit(trade, currentPrice);
            }

            // Проверяем, превышает ли процент прибыли заданный порог (0.5%)
            if (pnlPercentage > 1.5) {
                // Если да, обновляем уровни стоп-лосс и тейк-профит для данной сделки
                //updateStopLossAndTakeProfit(trade, currentPrice);
                ordersToRemove.add(trade);
            }

            // Проверяем, был ли ордер исполнен
            if (isOrderExecuted(trade, currentPrice)) {
                // Если ордер исполнен, добавляем его в список на удаление
                ordersToRemove.add(trade);
            }
        }

        // Удаляем все исполненные ордера из списка активных ордеров
        activeOrders.removeAll(ordersToRemove);
    }

    /**
     * Рассчитывает нереализованную прибыль или убыток для данной сделки на основе текущей цены.
     *
     * @param trade         Сделка, для которой рассчитывается PnL.
     * @param currentPrice  Текущая рыночная цена.
     * @return Нереализованная прибыль или убыток.
     */
    private BigDecimal calculateUnrealizedPnl(Trade trade, BigDecimal currentPrice) {
        BigDecimal entryPrice = BigDecimal.valueOf(trade.getEntryPrice());
        BigDecimal amount = BigDecimal.valueOf(trade.getAmount());

        // Рассчитываем PnL в зависимости от направления сделки
        if (trade.getSide() == Side.BUY) {
            // Для сделки на покупку PnL = (текущая цена - цена входа) * количество
            return currentPrice.subtract(entryPrice).multiply(amount);
        } else {
            // Для сделки на продажу PnL = (цена входа - текущая цена) * количество
            return entryPrice.subtract(currentPrice).multiply(amount);
        }
    }

    /**
     * Обновляет уровни Stop Loss и Take Profit для сделки на основе текущей цены.
     *
     * @param trade         Сделка, для которой обновляются уровни.
     * @param currentPrice  Текущая рыночная цена.
     */
    private void updateStopLossAndTakeProfit(Trade trade, BigDecimal currentPrice) {
        // Устанавливаем новый стоп-лосс на 0.01 ниже текущей цены
        BigDecimal newStopLoss = currentPrice.subtract(BigDecimal.valueOf(0.01));

        // Обновляем стоп-лосс и тейк-профит в объекте сделки
        trade.setStopLoss(newStopLoss.doubleValue());

        // обновить ордер через Bybit API
        bybitClient.updateOrder(trade);
    }

    /**
     * Проверяет, был ли ордер исполнен на основе текущей цены.
     * Учитывает как достижение Take Profit, так и Stop Loss для обеих сторон сделки.
     *
     * @param trade         Сделка, которую необходимо проверить.
     * @param currentPrice  Текущая рыночная цена.
     * @return true, если ордер был исполнен, иначе false.
     */
    private boolean isOrderExecuted(Trade trade, BigDecimal currentPrice) {
        // Если ордер на покупку, он исполнен, если текущая цена достигла тейк-профит или упала до стоп-лосс
        if (trade.getSide() == Side.BUY) {
            return currentPrice.compareTo(BigDecimal.valueOf(trade.getTakeProfit())) >= 0
                    || currentPrice.compareTo(BigDecimal.valueOf(trade.getStopLoss())) <= 0;
        }
        // Если ордер на продажу, он исполнен, если текущая цена достигла тейк-профит или поднялась до стоп-лосс
        else {
            return currentPrice.compareTo(BigDecimal.valueOf(trade.getTakeProfit())) <= 0
                    || currentPrice.compareTo(BigDecimal.valueOf(trade.getStopLoss())) >= 0;
        }
    }
}
