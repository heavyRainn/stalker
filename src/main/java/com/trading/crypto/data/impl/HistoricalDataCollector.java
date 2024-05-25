package com.trading.crypto.data.impl;

import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.market.MarketInterval;
import com.bybit.api.client.domain.market.request.MarketDataRequest;
import com.bybit.api.client.service.BybitApiClientFactory;
import com.trading.crypto.analyzer.Analyser;
import com.trading.crypto.data.DataCollector;
import com.trading.crypto.model.KlineElement;
import com.trading.crypto.util.StalkerUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Для сбора и хранения исторических данных
 */
@Slf4j
@Service
public class HistoricalDataCollector implements DataCollector {

    public List<String> symbols;
    private List<MarketInterval> intervals;

    @Setter
    private Analyser analyser;

    /**
     * Карта, содержащая исторические данные торгов для различных торговых символов и интервалов.
     * Ключ верхнего уровня Map представляет собой строку, обозначающую торговый символ (например, "BTCUSDT").
     * Значение для каждого символа - это другой Map, где ключ - это перечисление {@link MarketInterval},
     * обозначающее интервал времени (например, ONE_MINUTE, FIVE_MINUTES и т.д.).
     * <p>
     * Вложенный список {@link KlineElement} содержит элементы данных Kline, которые представляют собой
     * информацию о торгах за определённый интервал. Эти списки упорядочены по времени в убывающем порядке,
     * то есть более ранние торговые данные расположены ближе к началу списка, а более поздние - к концу.
     * Это обеспечивает быстрый доступ к последним данным торгов.
     * <p>
     * Структура данных обеспечивает эффективное хранение и управление историческими данными для множественных
     * торговых символов и временных интервалов, что позволяет проводить анализ торгов на разных уровнях детализации.
     */
    @Getter
    private final Map<String, Map<MarketInterval, List<KlineElement>>> klineCache = new HashMap<>();

    public void init(List<String> symbols, List<MarketInterval> intervals) {
        this.symbols = symbols;
        this.intervals = intervals;

        intervals.forEach(interval -> symbols.forEach(symbol -> pullKline(symbol, interval)));
    }

    @Scheduled(fixedDelay = 60000)
    private void streamKline() {
        if (klineCache.isEmpty()) {
            return;
        }

        // подтягивание значений по каждому символу и интервалу
        intervals.forEach(interval -> symbols.forEach(symbol -> {
            List<KlineElement> elements = klineCache.get(symbol).get(interval);
            if (elements != null && !elements.isEmpty()) {
                pullKline(symbol, interval, elements.get(0).getTimestamp());
            } else {
                log.error("No data available for symbol {} and interval {}", symbol, interval);
            }
        }));
    }

    /**
     * Метод для очистки и сортировки кэша Kline.
     * Срабатывает раз в 30 минут.
     */
    @Scheduled(fixedRate = 1800000) // 1800000 миллисекунд = 30 минут
    public void cleanAndSortKlineCache() {
        // Проходим по каждому символу
        for (Map<MarketInterval, List<KlineElement>> intervalMap : klineCache.values()) {
            // Для каждого интервала обрабатываем список KlineElements
            for (List<KlineElement> intervalKlineCache : intervalMap.values()) {
                synchronized (klineCache) {
                    // Ограничение размера списка до 250 элементов, если необходимо
                    if (intervalKlineCache.size() > 250) {
                        intervalKlineCache.subList(250, intervalKlineCache.size()).clear();
                    }
                }
            }
        }
    }

    private void pullKline(String symbol, MarketInterval interval, Long startTimestamp) {
        var client = BybitApiClientFactory.newInstance().newAsyncMarketDataRestClient();
        var marketKLineRequest = MarketDataRequest.builder()
                .category(CategoryType.LINEAR)
                .symbol(symbol)
                .marketInterval(interval)
                .start(startTimestamp)
                .build();

        client.getMarketLinesData(marketKLineRequest, response -> processSingeKlineElement(response, symbol, interval));
    }

    private void pullKline(String symbol, MarketInterval interval) {
        var client = BybitApiClientFactory.newInstance().newAsyncMarketDataRestClient();
        var marketKLineRequest = MarketDataRequest.builder()
                .category(CategoryType.LINEAR)
                .symbol(symbol)
                .marketInterval(interval)
                .build();

        client.getMarketLinesData(marketKLineRequest, (response) -> processSingeKlineElement(response, symbol, interval));
    }

    private void processSingeKlineElement(Object response, String symbol, MarketInterval interval) {
        Optional.ofNullable(response).ifPresent(data -> {
            // Преобразуем JSON в LinkedHashMap
            Map<String, Object> map = StalkerUtils.getMapFromResponse(data);

            LinkedHashMap<Object, Object> result = (LinkedHashMap<Object, Object>) map.get("result");
            List<List<Object>> list = (List<List<Object>>) result.get("list");

            synchronized (klineCache) {
                // Убедиться, что существует соответствующий список для symbol и interval
                klineCache.computeIfAbsent(symbol, k -> new HashMap<>())
                        .computeIfAbsent(interval, k -> new LinkedList<>());

                List<KlineElement> symbolKlineCache = klineCache.get(symbol).get(interval);
                Optional.ofNullable(list).ifPresent(klineSingleData -> {
                    for (int i = klineSingleData.size() - 1; i >= 0; i--) {
                        KlineElement klineElement = toKlineElement(klineSingleData.get(i));

                        // Предотвратить добавление дубликатов
                        if (!symbolKlineCache.isEmpty() && symbolKlineCache.get(0).getTimestamp().equals(klineElement.getTimestamp())) {
                            continue;
                        }

                        symbolKlineCache.add(0, klineElement);
                        if (analyser != null) {
                            analyser.update(symbol, interval, klineElement);
                        }
                    }
                });
            }
        });
    }


    /**
     * Convert Object to Kline element
     *
     * @param objects list of Object data per bar
     * @return KlineElement
     */
    private KlineElement toKlineElement(List<Object> objects) {
        return new KlineElement(Long.parseLong(
                objects.get(0).toString()), // startTime
                new BigDecimal(objects.get(1).toString()), // openPrice
                new BigDecimal(objects.get(2).toString()), // highPrice
                new BigDecimal(objects.get(3).toString()), // lowPrice
                new BigDecimal(objects.get(4).toString()),  // closePrice
                new BigDecimal(objects.get(5).toString()), // volume
                new BigDecimal(objects.get(6).toString())); // turnover
    }

}
