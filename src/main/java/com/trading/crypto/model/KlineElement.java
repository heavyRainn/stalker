package com.trading.crypto.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * https://bybit-exchange.github.io/docs/v5/market/mark-kline#http-request
 */
@Data
@AllArgsConstructor
public class KlineElement {
    private Long timestamp;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private BigDecimal volume;
    private BigDecimal turnover;
}
