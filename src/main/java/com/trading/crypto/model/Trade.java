package com.trading.crypto.model;

import com.bybit.api.client.domain.trade.Side;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Trade {
    private String symbol;
    private double entryPrice;
    private double stopLoss;
    private double takeProfit;
    private double amount;
    private Side side;
}
