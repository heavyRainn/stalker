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
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConvergenceDivergenceIndicator;
import org.ta4j.core.indicators.helpers.ConvergenceDivergenceIndicator.ConvergenceDivergenceType;
import org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Убедись, что интерфейс поддерживает расчет различных типов индикаторов. Возможно, стоит предусмотреть возможность
 * расширения для добавления новых индикаторов без изменения основного кода.
 */
@Slf4j
public class IndicatorAnalyzer implements Analyser {

    public static final int CCI_LOW = -200;
    public static final int CCI_HIGH = 200;
    public static final int RSI_LOW = 30;
    public static final int RSI_HIGH = 70;

    private static final int CHECK_PERIOD = 20;

    private final String symbol;

    private final Map<MarketInterval, TimeSeries> seriesMap = new HashMap<>();
    private final Map<MarketInterval, SMAIndicator> smaMap = new HashMap<>();
    private final Map<MarketInterval, RSIIndicator> rsiMap = new HashMap<>();
    private final Map<MarketInterval, CCIIndicator> cciMap = new HashMap<>();


    public IndicatorAnalyzer(Map<String, Map<MarketInterval, List<KlineElement>>> cache, String symbol) {
        this.symbol = symbol;

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
                log.trace("Attempted to add bar with end time " + newBar.getEndTime() + " that is not after series end time " + series.getLastBar().getEndTime());
            }
        } else {
            log.error("No series found for interval " + interval);
        }
    }

    @Override
    public Map<MarketInterval, Signal> analyze(List<MarketInterval> intervals) {
        return intervals.stream()
                .distinct()
                .map(interval -> Map.entry(interval, calculateIndicators(interval)))
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (existing, replacement) -> replacement));
    }

    public Signal calculateIndicators(MarketInterval interval) {
        TimeSeries series = seriesMap.get(interval);
        if (series == null || series.getBarCount() == 0) {
            log.error("No data available for {} at interval {}", symbol, interval);
            return null;
        }

        SMAIndicator sma = smaMap.get(interval);
        RSIIndicator rsi = rsiMap.get(interval);
        CCIIndicator cci = cciMap.get(interval);

        int lastIndex = series.getEndIndex();
        Num lastPrice = series.getBar(lastIndex).getClosePrice();
        Num lastRSI = rsi.getValue(lastIndex);
        Num lastCCI = cci.getValue(lastIndex);
        Num lastSMA = sma.getValue(lastIndex);

        boolean isPriceAboveSMA = lastPrice.isGreaterThan(lastSMA);

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // Использование ConvergenceDivergenceIndicator для определения дивергенций
        ConvergenceDivergenceIndicator cciPositiveDivergent = new ConvergenceDivergenceIndicator(closePrice, cci, CHECK_PERIOD, ConvergenceDivergenceType.positiveDivergent);
        ConvergenceDivergenceIndicator cciNegativeDivergent = new ConvergenceDivergenceIndicator(closePrice, cci, CHECK_PERIOD, ConvergenceDivergenceType.negativeDivergent);
        ConvergenceDivergenceIndicator rsiPositiveDivergent = new ConvergenceDivergenceIndicator(closePrice, rsi, CHECK_PERIOD, ConvergenceDivergenceType.positiveDivergent);
        ConvergenceDivergenceIndicator rsiNegativeDivergent = new ConvergenceDivergenceIndicator(closePrice, rsi, CHECK_PERIOD, ConvergenceDivergenceType.negativeDivergent);

        boolean bullishCciDivergence = cciPositiveDivergent.getValue(lastIndex);
        boolean bearishCciDivergence = cciNegativeDivergent.getValue(lastIndex);
        boolean bullishRsiDivergence = rsiPositiveDivergent.getValue(lastIndex);
        boolean bearishRsiDivergence = rsiNegativeDivergent.getValue(lastIndex);

        // Log analysis before making decisions
        LogUtils.logAnalysis(symbol, interval, lastPrice, lastRSI, lastCCI, lastSMA, isPriceAboveSMA, bullishRsiDivergence,
                bearishRsiDivergence, bullishCciDivergence, bearishCciDivergence);

        AnalysisResult signal;
        if (lastCCI.isLessThan(PrecisionNum.valueOf(CCI_LOW + 30)) && lastRSI.isLessThan(PrecisionNum.valueOf(RSI_LOW))) {
            if (bullishCciDivergence && bullishRsiDivergence) {
                signal = AnalysisResult.STRONG_BUY;
            } else {
                signal = AnalysisResult.BUY;
            }

        } else if (lastCCI.isGreaterThan(PrecisionNum.valueOf(CCI_HIGH - 30)) && lastRSI.isGreaterThan(PrecisionNum.valueOf(RSI_HIGH))) {
            if (bearishCciDivergence || bearishRsiDivergence) {
                signal = AnalysisResult.STRONG_SELL;
            } else {
                signal = AnalysisResult.SELL;
            }
        } else {
            signal = AnalysisResult.HOLD;
        }

        return new Signal(signal, symbol, lastPrice, System.currentTimeMillis());
    }

}
