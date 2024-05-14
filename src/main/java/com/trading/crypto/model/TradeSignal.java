package com.trading.crypto.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradeSignal {
    private AnalysisResult signalType; // Тип сигнала: BUY, SELL, STRONG_BUY, STRONG_SELL
    private String symbol;            // Символ монеты
    private double entryPrice;        // Цена входа
    private double stopLoss;          // Уровень Stop-Loss
    private double takeProfit;        // Уровень Take-Profit
    private double amount;            // Количество лотов для входа в сделку
    private long timestamp;           // Время создания сингала
}
