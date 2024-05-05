package com.trading.crypto.analyzer.impl;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.analyzer.Analyser;
import com.trading.crypto.model.AnalysisResult;
import com.trading.crypto.model.KlineElement;
import com.trading.crypto.model.Signal;
import com.trading.crypto.util.LogUtils;
import com.trading.crypto.util.StalkerUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *  Убедись, что интерфейс поддерживает расчет различных типов индикаторов. Возможно, стоит предусмотреть возможность
 *  расширения для добавления новых индикаторов без изменения основного кода.
 */
@Slf4j
public class IndicatorAnalyzer implements Analyser {

    public static final int CCI_LOW = -200;
    public static final int CCI_HIGH = 200;
    public static final int RSI_LOW = 30;
    public static final int RSI_HIGH = 70;

    private final String symbol;

    private final Map<MarketInterval, TimeSeries> seriesMap = new HashMap<>();
    private final Map<MarketInterval, SMAIndicator> smaMap = new HashMap<>();
    private final Map<MarketInterval, RSIIndicator> rsiMap = new HashMap<>();
    private final Map<MarketInterval, CCIIndicator> cciMap = new HashMap<>();

    @Getter
    private final List<Signal> signalHistory;

    public IndicatorAnalyzer(Map<String, Map<MarketInterval, List<KlineElement>>> cache, String symbol) {
        this.symbol = symbol;
        this.signalHistory = new ArrayList<>();

        // Инициализация TimeSeries и индикаторов для каждого таймфрейма и символа
        if (cache.containsKey(symbol)) {
            Map<MarketInterval, List<KlineElement>> intervals = cache.get(symbol);
            for (MarketInterval interval : intervals.keySet()) {
                List<KlineElement> klineElements = intervals.get(interval);
                TimeSeries series = new BaseTimeSeries.SeriesBuilder().withName(symbol + "_" + interval.toString()).build();

                // Теперь добавляем каждый бар, проверяя, что его время окончания больше последнего в series
                // Добавление происходит из колекции в обратном порядке т.к. в кеше данные хранятся от старшего к младшему
                IntStream.range(0, klineElements.size())
                        .mapToObj(i -> klineElements.get(klineElements.size() - 1 - i))
                        .forEach(kline -> {
                            Bar newBar = StalkerUtils.convertToBaseBar(kline);
                            if (series.isEmpty() || newBar.getEndTime().isAfter(series.getBar(series.getBarCount() - 1).getEndTime())) {
                                series.addBar(newBar);
                            } else {
                                log.warn("Skipped bar with end time " + newBar.getEndTime() + " as it is not after the series last bar end time.");
                            }
                        });

                // Сохранение TimeSeries в map
                seriesMap.put(interval, series);

                // Инициализация индикаторов
                ClosePriceIndicator cpi = new ClosePriceIndicator(series);
                smaMap.put(interval, new SMAIndicator(cpi, 100)); // Пример: SMA на 100 периодов
                rsiMap.put(interval, new RSIIndicator(cpi, 14)); // Пример: RSI на 14 периодов
                cciMap.put(interval, new CCIIndicator(series, 7)); // Пример: CCI на 20 периодов
            }
        }
    }

    @Override
    public void update(MarketInterval interval, KlineElement klineElement) {
        TimeSeries series = seriesMap.get(interval);
        if (series != null) {
            // Преобразование KlineElement в BaseBar
            Bar newBar = StalkerUtils.convertToBaseBar(klineElement);

            // Проверка на наличие баров в серии и на то, что новый бар идёт после последнего
            if (series.isEmpty() || newBar.getEndTime().isAfter(series.getLastBar().getEndTime())) {
                series.addBar(newBar);
                log.trace("Added new bar to the series for interval " + interval + ": " + newBar);
            } else {
                log.trace("Attempted to add bar with end time " + newBar.getEndTime() +
                        " that is not after series end time " + series.getLastBar().getEndTime());
            }
        } else {
            log.error("No series found for interval " + interval);
        }
    }

    public Map<MarketInterval, AnalysisResult> calculateIndicators(List<MarketInterval> intervals) {
        return intervals.stream().collect(Collectors.toMap(interval -> interval, this::calculateIndicators));
    }

    public AnalysisResult calculateIndicators(MarketInterval interval) {
        TimeSeries series = seriesMap.get(interval);
        if (series == null || series.getBarCount() == 0) {
            log.error("No data available for {} at interval {}", symbol, interval);
            return AnalysisResult.HOLD;
        }

        SMAIndicator sma = smaMap.get(interval);
        RSIIndicator rsi = rsiMap.get(interval);
        CCIIndicator cci = cciMap.get(interval);

        int lastIndex = series.getEndIndex();
        Num lastPrice = series.getBar(lastIndex).getClosePrice();
        Num lastRSI = rsi.getValue(lastIndex);
        Num lastCCI = cci.getValue(lastIndex);
        Num lastSMA = sma.getValue(lastIndex);

        boolean isPriceBelowSMA = lastPrice.isLessThan(lastSMA);
        boolean isPriceAboveSMA = lastPrice.isGreaterThan(lastSMA);

        if (lastCCI.isLessThan(PrecisionNum.valueOf(CCI_LOW)) && lastRSI.isLessThan(PrecisionNum.valueOf(RSI_LOW))) {
            boolean bullishCciDivergence = checkBullishCciDivergence(interval, cci);
            if (bullishCciDivergence && isPriceBelowSMA) {
                signalHistory.add(new Signal(AnalysisResult.STRONG_BUY, symbol, lastPrice, System.currentTimeMillis()));

                LogUtils.logAnalysis(symbol, interval, lastPrice, lastRSI, lastCCI, lastSMA, true, isPriceAboveSMA,
                        false, false, false, true);

                return AnalysisResult.STRONG_BUY;
            }
            LogUtils.logAnalysis(symbol, interval, lastPrice, lastRSI, lastCCI, lastSMA, isPriceBelowSMA, isPriceAboveSMA);
            signalHistory.add(new Signal(AnalysisResult.BUY, symbol, lastPrice, System.currentTimeMillis()));
            return AnalysisResult.BUY;
        } else if (lastCCI.isGreaterThan(PrecisionNum.valueOf(CCI_HIGH)) && lastRSI.isGreaterThan(PrecisionNum.valueOf(RSI_HIGH))) {
            boolean bearishCciDivergence = checkBearishCciDivergence(interval, cci);
            if (bearishCciDivergence && isPriceAboveSMA) {
                signalHistory.add(new Signal(AnalysisResult.STRONG_SELL, symbol, lastPrice, System.currentTimeMillis()));

                LogUtils.logAnalysis(symbol, interval, lastPrice, lastRSI, lastCCI, lastSMA, isPriceBelowSMA,
                        true, false, false, false, true);

                return AnalysisResult.STRONG_SELL;
            }
            signalHistory.add(new Signal(AnalysisResult.SELL, symbol, lastPrice, System.currentTimeMillis()));
            LogUtils.logAnalysis(symbol, interval, lastPrice, lastRSI, lastCCI, lastSMA, isPriceBelowSMA, isPriceAboveSMA);
            return AnalysisResult.SELL;
        } else {
            // Log analysis before making decisions
            LogUtils.logAnalysis(symbol, interval, lastPrice, lastRSI, lastCCI, lastSMA, isPriceBelowSMA, isPriceAboveSMA);
            return AnalysisResult.HOLD;
        }
    }

    private boolean checkBullishCciDivergence(MarketInterval interval, CCIIndicator cci) {
        TimeSeries series = seriesMap.get(interval);
        int endIndex = series.getEndIndex();
        int divergenceCount = 0;

        Num minCCI = cci.getValue(endIndex);
        Num minPrice = series.getBar(endIndex).getClosePrice();

        for (int i = endIndex - 1; i >= Math.max(endIndex - 20, 0) && divergenceCount < 3; i--) {
            Num price = series.getBar(i).getClosePrice();
            Num cciValue = cci.getValue(i);
            if (price.isLessThan(minPrice) && cciValue.isGreaterThan(minCCI)) {
                divergenceCount++;
                minCCI = cciValue;
                minPrice = price;
            }
        }
        return divergenceCount >= 3;
    }

    private boolean checkBearishCciDivergence(MarketInterval interval, CCIIndicator cci) {
        TimeSeries series = seriesMap.get(interval);
        int endIndex = series.getEndIndex();
        int divergenceCount = 0;

        Num maxCCI = cci.getValue(endIndex);
        Num maxPrice = series.getBar(endIndex).getClosePrice();

        for (int i = endIndex - 1; i >= Math.max(endIndex - 20, 0) && divergenceCount < 3; i--) {
            Num price = series.getBar(i).getClosePrice();
            Num cciValue = cci.getValue(i);
            if (price.isGreaterThan(maxPrice) && cciValue.isLessThan(maxCCI)) {
                divergenceCount++;
                maxCCI = cciValue;
                maxPrice = price;
            }
        }
        return divergenceCount >= 3;
    }

    private boolean checkBullishRsiDivergence(MarketInterval interval, RSIIndicator rsi) {
        TimeSeries series = seriesMap.get(interval);
        int endIndex = series.getEndIndex();
        int divergenceCount = 0;

        Num minRSI = rsi.getValue(endIndex);
        Num minPrice = series.getBar(endIndex).getClosePrice();

        for (int i = endIndex - 1; i >= Math.max(endIndex - 20, 0) && divergenceCount < 3; i--) {
            Num price = series.getBar(i).getClosePrice();
            Num rsiValue = rsi.getValue(i);
            if (price.isLessThan(minPrice) && rsiValue.isGreaterThan(minRSI)) {
                divergenceCount++;
                minRSI = rsiValue;
                minPrice = price;
            }
        }
        return divergenceCount >= 3;
    }

    private boolean checkBearishRsiDivergence(MarketInterval interval, RSIIndicator rsi) {
        TimeSeries series = seriesMap.get(interval);
        int endIndex = series.getEndIndex();
        int divergenceCount = 0;

        Num maxRSI = rsi.getValue(endIndex);
        Num maxPrice = series.getBar(endIndex).getClosePrice();

        for (int i = endIndex - 1; i >= Math.max(endIndex - 20, 0) && divergenceCount < 3; i--) {
            Num price = series.getBar(i).getClosePrice();
            Num rsiValue = rsi.getValue(i);
            if (price.isGreaterThan(maxPrice) && rsiValue.isLessThan(maxRSI)) {
                divergenceCount++;
                maxRSI = rsiValue;
                maxPrice = price;
            }
        }
        return divergenceCount >= 3;
    }
}
