package com.trading.crypto.model;

/**
 * STRONG BUY/SELL: Используется, когда все три индикатора (RSI, CCI, и положение относительно SMA) указывают на крайние условия.
 * BUY/SELL: Поддерживает идею, что если цена выше/ниже SMA, это может быть хорошим моментом для входа, соответствуя общему тренду.
 * HOLD: Используется, когда условия
 */
public enum AnalysisResult {
    STRONG_BUY,
    STRONG_SELL,
    BUY,
    SELL,
    HOLD
}
