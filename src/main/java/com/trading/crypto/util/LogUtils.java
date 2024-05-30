package com.trading.crypto.util;

import com.bybit.api.client.domain.market.MarketInterval;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.trading.crypto.model.*;
import lombok.extern.slf4j.Slf4j;
import org.ta4j.core.num.Num;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class LogUtils {

    // ANSI Escape Codes for Colors
    private static final String RESET = "\033[0m";
    private static final String YELLOW = "\033[33m";
    private static final String GREEN = "\033[32m";
    private static final String RED = "\033[31m";
    private static final String BLUE = "\033[34m";
    private static final String PURPLE = "\033[35m";
    private static final String CYAN = "\033[36m";
    private static final String MAGENTA = "\033[35m";

    // Custom Colors for Trade Signals
    private static final String STRONG_BUY_COLOR = "\033[92m"; // Light Green
    private static final String STRONG_SELL_COLOR = "\033[91m"; // Light Red
    private static final String BUY_COLOR = GREEN;
    private static final String SELL_COLOR = RED;
    private static final String HOLD_COLOR = YELLOW;

    // Constants for Repeated Strings
    private static final String SYMBOL_HEADER = "\n\033[33m=== Symbol: %s, TimeFrame: %s ===\033[0m";
    private static final String DIVERGENCES_HEADER = "Divergences ->";
    private static final String BULLISH_RSI = "\033[32mBullish RSI:\033[0m";
    private static final String BEARISH_RSI = "\033[31mBearish RSI:\033[0m";
    private static final String BULLISH_CCI = "\033[32mBullish CCI:\033[0m";
    private static final String BEARISH_CCI = "\033[31mBearish CCI:\033[0m";
    private static final String UP_TREND = "\033[36muptrend\033[0m";
    private static final String DOWN_TREND = "\033[35mdowntrend\033[0m";

    private static final String PRESENT = "Present";
    private static final String ABSENT = "Absent";

    // Trade Signal Constants
    private static final String TRADE_SIGNAL_FORMAT = "%sSymbol: %s, Type: %s, Entry Price: %.2f, Stop Loss: %.2f, Take Profit: %.2f, Amount: %.2f, Origin: %s, Timestamp: %s%s";
    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Логирование результатов анализа для каждого временного интервала
     *
     * @param analysisResults - список результатов анализа
     */
    public static void logAnalysis(List<Signal> analysisResults) {
        if (analysisResults.isEmpty()) {
            log.info("No analysis results to display.");
            return;
        }

        StringBuilder logMessage = new StringBuilder("\nAnalysis Results:");
        analysisResults.forEach(signal -> {
            logMessage.append("\n\tInterval: \033[35m").append(signal.getInterval()).append("\033[0m")
                    .append(" - Result: ").append(formatResult(signal.getAnalysisResult()))
                    .append(", Pair: ").append(signal.getAsset())
                    .append(", Price: ").append(signal.getPrice())
                    .append(", Timestamp: ").append(formatTimestamp(signal.getTimestamp()));
        });

        log.info(logMessage.toString());
    }

    /**
     * Форматирование метки времени для логирования
     *
     * @param timestamp - метка времени
     * @return отформатированная строка метки времени
     */
    private static String formatTimestamp(long timestamp) {
        return new SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new Date(timestamp));
    }

    /**
     * Сохранение торговых сигналов в JSON файл
     *
     * @param signals список торговых сигналов
     */
    public static void logTradeSignalsToFile(List<TradeSignal> signals) {
        // Группировка сигналов по символам
        Map<String, List<TradeSignal>> signalsBySymbol = signals.stream()
                .collect(Collectors.groupingBy(TradeSignal::getSymbol));

        signalsBySymbol.forEach((symbol, symbolSignals) -> {
            List<TradeSignal> existingSignals = new ArrayList<>();

            // Сначала считываем существующие данные
            File file = new File(symbol + "_signals.json");
            if (file.exists()) {
                try {
                    existingSignals = mapper.readValue(file, new TypeReference<>() {});
                } catch (IOException e) {
                    System.err.println("Ошибка при чтении файла: " + e.getMessage());
                }
            }

            // Создаем новый список, объединяющий новые и существующие сигналы
            List<TradeSignal> allSignals = new ArrayList<>(existingSignals);
            allSignals.addAll(symbolSignals);

            // Перезаписываем файл с обновленным списком
            try {
                mapper.writeValue(file, allSignals);
            } catch (IOException e) {
                System.err.println("Ошибка при записи в файл: " + e.getMessage());
            }
        });
    }

    /**
     * Логирование торговых сигналов с цветным выводом
     *
     * @param signals - список торговых сигналов
     */
    public static void logTradeSignals(List<TradeSignal> signals) {
        StringBuilder logMessage = new StringBuilder("\n=== Trade Signals ===\n");

        for (TradeSignal signal : signals) {
            String color = getColorForSignal(signal.getSignalType());
            String signalInfo = String.format(TRADE_SIGNAL_FORMAT, color, signal.getSymbol(), signal.getSignalType(), signal.getEntryPrice(),
                    signal.getStopLoss(), signal.getTakeProfit(), signal.getAmount(), signal.getOrigin(), formatTimestamp(signal.getTimestamp()), RESET);
            logMessage.append(signalInfo).append("\n");
        }

        log.info(logMessage.toString());
    }

    /**
     * Форматирование результата анализа в цветном виде
     *
     * @param result - результат анализа
     * @return отформатированная строка
     */
    private static String formatResult(AnalysisResult result) {
        return switch (result) {
            case STRONG_BUY -> STRONG_BUY_COLOR + AnalysisResult.STRONG_BUY + RESET; // Bright Green
            case BUY -> BUY_COLOR + AnalysisResult.BUY + RESET; // Green
            case STRONG_SELL -> STRONG_SELL_COLOR + AnalysisResult.STRONG_SELL + RESET; // Bright Red
            case SELL -> SELL_COLOR + AnalysisResult.SELL + RESET; // Red
            case HOLD -> HOLD_COLOR + AnalysisResult.HOLD + RESET; // Yellow
        };
    }

    /**
     * Получение цвета для определенного типа торгового сигнала
     *
     * @param signalType - тип сигнала
     * @return цвет ANSI
     */
    private static String getColorForSignal(AnalysisResult signalType) {
        return switch (signalType) {
            case STRONG_BUY -> STRONG_BUY_COLOR;
            case BUY -> BUY_COLOR;
            case STRONG_SELL -> STRONG_SELL_COLOR;
            case SELL -> SELL_COLOR;
            default -> HOLD_COLOR;
        };
    }

    /**
     * Логирование анализа индикаторов и дивергенций
     *
     * @param symbol               - торговый символ (например, BTCUSDT)
     * @param interval             - временной интервал (например, MarketInterval.ONE_MINUTE)
     * @param lastPrice            - последняя цена
     * @param lastRSI              - последнее значение RSI
     * @param lastCCI              - последнее значение CCI
     * @param lastSMA              - последнее значение SMA
     * @param isPriceAboveSMA      - находится ли цена выше SMA
     * @param bullishRsiDivergence - есть ли бычья дивергенция RSI
     * @param bearishRsiDivergence - есть ли медвежья дивергенция RSI
     * @param bullishCciDivergence - есть ли бычья дивергенция CCI
     * @param bearishCciDivergence - есть ли медвежья дивергенция CCI
     */
    public static void logAnalysis(String symbol, MarketInterval interval,
                                   Num lastPrice, Num lastRSI, Num lastCCI, Num lastSMA, boolean isPriceAboveSMA,
                                   Boolean bullishRsiDivergence, Boolean bearishRsiDivergence,
                                   Boolean bullishCciDivergence, Boolean bearishCciDivergence) {

        // Заголовок
        String header = String.format(SYMBOL_HEADER, symbol, interval); // Желтый для заголовка

        // Детальные цены и индикаторы
        String prices = String.format("\033[34mPrice:\033[0m %s, \033[32mRSI:\033[0m %s, \033[35mCCI:\033[0m %s, \033[36mSMA:\033[0m %s, %s",
                lastPrice, lastRSI, lastCCI, lastSMA,
                isPriceAboveSMA ? UP_TREND : DOWN_TREND); // Синий, Зеленый, Пурпурный, Голубой для индикаторов

        // Информация о дивергенциях
        String divergences = formatDivergenceInfo(bullishRsiDivergence, bearishRsiDivergence,
                bullishCciDivergence, bearishCciDivergence);

        log.info("{}\n{}\n{} {}", header, prices, DIVERGENCES_HEADER, divergences);
    }

    /**
     * Форматирование информации о дивергенциях в цветном виде
     *
     * @param bullishRsiDivergence - бычья дивергенция RSI
     * @param bearishRsiDivergence - медвежья дивергенция RSI
     * @param bullishCciDivergence - бычья дивергенция CCI
     * @param bearishCciDivergence - медвежья дивергенция CCI
     * @return отформатированная строка
     */
    private static String formatDivergenceInfo(Boolean bullishRsiDivergence, Boolean bearishRsiDivergence,
                                               Boolean bullishCciDivergence, Boolean bearishCciDivergence) {
        return String.format("%s %s, %s %s, %s %s, %s %s",
                BULLISH_RSI, bullishRsiDivergence != null && bullishRsiDivergence ? PRESENT : ABSENT,
                BEARISH_RSI, bearishRsiDivergence != null && bearishRsiDivergence ? PRESENT : ABSENT,
                BULLISH_CCI, bullishCciDivergence != null && bullishCciDivergence ? PRESENT : ABSENT,
                BEARISH_CCI, bearishCciDivergence != null && bearishCciDivergence ? PRESENT : ABSENT);
    }

    /**
     * Логирование информации о торговых сигналах
     *
     * @param signal     - торговый сигнал
     * @param evaluation - оценка риска
     */
    public static void logSignalInfo(TradeSignal signal, RiskEvaluation evaluation) {
        log.info("Signal for {}: Type: {}, Risk Level: {}, Recommended Lot Size: {}",
                signal.getSymbol(), signal.getSignalType(), evaluation, signal.getAmount());
    }

    /**
     * Логирование результатов анализа пин-баров
     *
     * @param pinBarSignals - список сигналов пин-баров
     */
    public static void logPinBarSignals(List<PinBarSignal> pinBarSignals) {
        if (pinBarSignals.isEmpty()) {
            log.info("No pin bar signals to display.");
            return;
        }

        StringBuilder logMessage = new StringBuilder("Pin Bar Analysis Results:");
        pinBarSignals.forEach(signal -> {
            String intervalColor = getIntervalColor(signal.getInterval());
            String resultColor = getResultColor(signal.getResult());
            logMessage.append("\n\tSymbol: ").append(signal.getSymbol())
                    .append(", Interval: ").append(intervalColor).append(signal.getInterval()).append(RESET)
                    .append(", Result: ").append(resultColor).append(signal.getResult()).append(RESET);
        });

        log.info(logMessage.toString());
    }

    /**
     * Получение цвета для временного интервала
     *
     * @param interval - временной интервал
     * @return цвет ANSI
     */
    private static String getIntervalColor(MarketInterval interval) {
        return switch (interval) {
            case ONE_MINUTE -> BLUE;
            case FIVE_MINUTES -> CYAN;
            case HOURLY -> PURPLE;
            default -> RESET;
        };
    }

    /**
     * Получение цвета для результата анализа пин-бара
     *
     * @param result - результат анализа пин-бара
     * @return цвет ANSI
     */
    private static String getResultColor(PinBarAnalysisResult result) {
        return switch (result) {
            case BULLISH_PIN_BAR -> GREEN;
            case BEARISH_PIN_BAR -> RED;
            case NO_PIN_BAR -> YELLOW;
            default -> RESET;
        };
    }
}
