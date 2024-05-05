package com.trading.crypto.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Trade {
    private double entryPrice;
    private double stopLoss;
    private double takeProfit;
    private double amount;
}
