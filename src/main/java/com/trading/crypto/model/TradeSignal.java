package com.trading.crypto.model;

import lombok.*;

import java.text.SimpleDateFormat;
import java.util.Date;

@Data
@NoArgsConstructor
public class TradeSignal {
    private AnalysisResult signalType; // Тип сигнала: BUY, SELL, STRONG_BUY, STRONG_SELL
    private String symbol;            // Символ монеты
    private double entryPrice;        // Цена входа
    private double stopLoss;          // Уровень Stop-Loss
    private double takeProfit;        // Уровень Take-Profit
    private double amount;            // Количество лотов для входа в сделку
    private long timestamp;           // Время создания сингала
    private String formattedTimestamp; // Форматированное время создания сигнала
    private SignalOrigin origin;

    public TradeSignal(AnalysisResult signalType, String symbol, double entryPrice, double stopLoss, double takeProfit, SignalOrigin signalOrigin, long timestamp) {
        this.signalType = signalType;
        this.symbol = symbol;
        this.entryPrice = entryPrice;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.timestamp = timestamp;
        this.origin = signalOrigin;
        this.formattedTimestamp = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new Date(timestamp));
    }
}
