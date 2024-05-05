package com.trading.crypto.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.crypto.model.KlineElement;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.PrecisionNum;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

public class StalkerUtils {
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
}
