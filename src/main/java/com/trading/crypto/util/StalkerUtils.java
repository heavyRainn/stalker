package com.trading.crypto.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.crypto.model.KlineElement;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.PrecisionNum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

public class StalkerUtils {

    // Список символов, для которых округляем amount до целого числа
    private static final Set<String> WHOLE_NUMBER_SYMBOLS = new HashSet<>(Arrays.asList("FTMUSDT", "GMTUSDT", "ADAUSDT"));

    public static BaseBar convertToBaseBar(KlineElement klineElement) {
        return new BaseBar(
                Duration.ofMinutes(1), // Продолжительность бара, например, 1 минута
                Instant.ofEpochMilli(klineElement.getTimestamp()).atZone(ZoneId.systemDefault()),
                PrecisionNum.valueOf(klineElement.getOpenPrice().doubleValue()),
                PrecisionNum.valueOf(klineElement.getHighPrice().doubleValue()),
                PrecisionNum.valueOf(klineElement.getLowPrice().doubleValue()),
                PrecisionNum.valueOf(klineElement.getClosePrice().doubleValue()),
                PrecisionNum.valueOf(klineElement.getVolume().doubleValue()),
                PrecisionNum.valueOf(klineElement.getTurnover().doubleValue()));
    }

    public static Map<String, Object> getMapFromResponse(Object response) {
        // Создаём объект ObjectMapper для разбора JSON
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map;
        try {
            map = mapper.readValue(mapper.writeValueAsString(response), new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return map;
    }

    /**
     * Возвращает форматированное значение amount в зависимости от символа.
     *
     * @param amount значение для округления
     * @param symbol символ торговой пары
     * @return отформатированное значение amount
     */
    public static String getFormattedAmount(double amount, String symbol) {
        int newScale = 1; // Значение по умолчанию

        // Проверка символа и установка нужного масштаба
        if (WHOLE_NUMBER_SYMBOLS.contains(symbol)) {
            newScale = 0;
        }

        BigDecimal amountBigDecimal = BigDecimal.valueOf(amount).setScale(newScale, RoundingMode.DOWN);
        amountBigDecimal = amountBigDecimal.stripTrailingZeros();
        return amountBigDecimal.toPlainString();
    }
}
