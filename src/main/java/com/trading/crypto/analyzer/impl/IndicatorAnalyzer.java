package com.trading.crypto.analyzer.impl;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.analyzer.Analyser;
import com.trading.crypto.model.AnalysisResult;
import com.trading.crypto.model.KlineElement;
import com.trading.crypto.model.Signal;
import com.trading.crypto.util.LogUtils;
import com.trading.crypto.util.StalkerUtils;
import lombok.extern.slf4j.Slf4j;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConvergenceDivergenceIndicator;
import org.ta4j.core.indicators.helpers.ConvergenceDivergenceIndicator.ConvergenceDivergenceType;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Класс для анализа индикаторов с использованием библиотеки Ta4j.
 * Поддерживает расчет и обновление индикаторов для различных символов и временных интервалов.
 * Также включает логику определения дивергенций и генерации торговых сигналов.
 */
@Slf4j
public class IndicatorAnalyzer implements Analyser {

    // Константы для границ CCI и RSI
    public static final int CCI_LOW = -210;
    public static final int CCI_HIGH = 210;
    public static final int RSI_LOW = 25;
    public static final int RSI_HIGH = 75;

    // Период для проверки дивергенций
    private static final int CHECK_PERIOD = 7;

    // Карты для хранения временных рядов и индикаторов для каждого символа и временного интервала
    private final Map<String, Map<MarketInterval, TimeSeries>> seriesMap = new HashMap<>();
    private final Map<String, Map<MarketInterval, SMAIndicator>> smaMap = new HashMap<>();
    private final Map<String, Map<MarketInterval, RSIIndicator>> rsiMap = new HashMap<>();
    private final Map<String, Map<MarketInterval, CCIIndicator>> cciMap = new HashMap<>();

    /**
     * Конструктор класса, инициализирует временные ряды и индикаторы для заданных символов и временных интервалов.
     *
     * @param cache   Кэш исторических данных для каждого символа и временного интервала
     * @param symbols Список символов для анализа
     */
    public IndicatorAnalyzer(Map<String, Map<MarketInterval, List<KlineElement>>> cache, List<String> symbols) {
        for (String symbol : symbols) {
            if (cache.containsKey(symbol)) {
                Map<MarketInterval, List<KlineElement>> intervals = cache.get(symbol);
                for (MarketInterval interval : intervals.keySet()) {
                    List<KlineElement> klineElements = intervals.get(interval);
                    TimeSeries series = new BaseTimeSeries.SeriesBuilder().withName(symbol + "_" + interval.toString()).build();

                    // Добавляем каждый бар
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
                    seriesMap.computeIfAbsent(symbol, k -> new HashMap<>()).put(interval, series);

                    // Инициализация индикаторов
                    ClosePriceIndicator cpi = new ClosePriceIndicator(series);
                    smaMap.computeIfAbsent(symbol, k -> new HashMap<>()).put(interval, new SMAIndicator(cpi, 100)); // Пример: SMA на 100 периодов
                    rsiMap.computeIfAbsent(symbol, k -> new HashMap<>()).put(interval, new RSIIndicator(cpi, 14)); // Пример: RSI на 14 периодов
                    cciMap.computeIfAbsent(symbol, k -> new HashMap<>()).put(interval, new CCIIndicator(series, 7)); // Пример: CCI на 20 периодов
                }
            }
        }
    }

    /**
     * Обновляет временной ряд для заданного символа и временного интервала новым элементом Kline.
     *
     * @param symbol       Символ, для которого обновляется временной ряд
     * @param interval     Временной интервал
     * @param klineElement Новый элемент Kline для добавления
     */
    public void update(String symbol, MarketInterval interval, KlineElement klineElement) {
        if (seriesMap.containsKey(symbol)) {
            TimeSeries series = seriesMap.get(symbol).get(interval);
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
        } else {
            log.error("No series found for symbol " + symbol);
        }
    }

    /**
     * Анализирует данные для заданного символа и списка временных интервалов.
     *
     * @param symbol    Символ для анализа
     * @param intervals Список временных интервалов
     * @return Список сигналов, сгенерированных на основе анализа индикаторов
     */
    public List<Signal> analyze(String symbol, List<MarketInterval> intervals) {
        return intervals.stream()
                .distinct()
                .map(interval -> calculateIndicators(symbol, interval))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Вычисляет индикаторы для заданного символа и временного интервала.
     *
     * @param symbol   Символ для анализа
     * @param interval Временной интервал
     * @return Сигнал, сгенерированный на основе анализа индикаторов
     */
    public Signal calculateIndicators(String symbol, MarketInterval interval) {
        if (seriesMap.containsKey(symbol)) {
            TimeSeries series = seriesMap.get(symbol).get(interval);
            if (series == null || series.getBarCount() == 0) {
                log.error("No data available for {} at interval {}", symbol, interval);
                return null;
            }

            SMAIndicator sma = smaMap.get(symbol).get(interval);
            RSIIndicator rsi = rsiMap.get(symbol).get(interval);
            CCIIndicator cci = cciMap.get(symbol).get(interval);

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

            // Логирование анализа перед принятием решений
            LogUtils.logAnalysis(symbol, interval, lastPrice, lastRSI, lastCCI, lastSMA, isPriceAboveSMA, bullishRsiDivergence,
                    bearishRsiDivergence, bullishCciDivergence, bearishCciDivergence);

            AnalysisResult signal;
            if (lastCCI.isLessThan(PrecisionNum.valueOf(CCI_LOW)) && lastRSI.isLessThan(PrecisionNum.valueOf(RSI_LOW))) {
                if (bullishCciDivergence || bullishRsiDivergence) {
                    signal = AnalysisResult.STRONG_BUY;
                } else {
                    signal = AnalysisResult.BUY;
                }
            } else if (lastCCI.isGreaterThan(PrecisionNum.valueOf(CCI_HIGH)) && lastRSI.isGreaterThan(PrecisionNum.valueOf(RSI_HIGH))) {
                if (bearishCciDivergence || bearishRsiDivergence) {
                    signal = AnalysisResult.STRONG_SELL;
                } else {
                    signal = AnalysisResult.SELL;
                }
            } else {
                signal = AnalysisResult.HOLD;
            }

            return new Signal(signal, symbol, lastPrice, System.currentTimeMillis(), interval);
        }
        return null;
    }
}
