package com.trading.crypto.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PositionInfo {
    private String symbol;
    private String side;
    private BigDecimal positionQty;
    private BigDecimal entryPrice;
    private BigDecimal markPrice;
    private BigDecimal liquidationPrice;
    private BigDecimal leverage;
    private BigDecimal unrealizedPnl;
    private BigDecimal realizedPnl;
    private BigDecimal takeProfit;
    private BigDecimal stopLoss;
    private BigDecimal trailingStop;
    private BigDecimal positionMargin;
    private String positionStatus;
    private Long positionId;
    private String category;
    private String baseCoin;
    private String settleCoin;
    private String tpTriggerBy;
    private String slTriggerBy;
    private String tpOrderType;
    private String slOrderType;
    private String tpslMode;
    private String tradeMode;
    private String positionMode;
    private String marginMode;
    private String autoAddMargin;
    private String blockTradeId;
}

