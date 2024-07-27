package com.trading.crypto.trader.impl;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.analyzer.impl.IndicatorAnalyzer;
import com.trading.crypto.analyzer.impl.PinBarDetector;
import com.trading.crypto.client.BybitClient;
import com.trading.crypto.data.impl.HistoricalDataCollector;
import com.trading.crypto.manager.RiskManager;
import com.trading.crypto.manager.StrategyManager;
import com.trading.crypto.model.*;
import com.trading.crypto.order.OrderExecutor;
import com.trading.crypto.order.impl.OrderMonitorService;
import com.trading.crypto.trader.Trader;
import com.trading.crypto.util.LogUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WaveTrader implements Trader {

    public static final List<String> symbols = List.of("FTMUSDT", "AVAXUSDT", "ADAUSDT", "DOGEUSDT", "GMTUSDT", "DOTUSDT", "1INCHUSDT", "NEARUSDT", "TONUSDT", "1000PEPEUSDT", "NOTUSDT", "OPUSDT");
    private final List<MarketInterval> intervals = List.of(MarketInterval.ONE_MINUTE);

    private final HistoricalDataCollector historicalDataCollector;
    private final OrderMonitorService orderMonitorService;
    private IndicatorAnalyzer indicatorAnalyzer;
    private final OrderExecutor orderExecutor;
    private final RiskManager riskManager;
    private final List<StrategyManager> strategyManagers;
    private final BybitClient bybitClient;

    private static final int REQUEST_INTERVAL = 5; // Запросить баланс каждые 5 проверок
    private int checkCounter = 0; // Счетчик проверок

    /**
     * Текущий баланс бота в USDT, обновляется после исполнения ордера или его создания
     */
    private BigDecimal balance;

    /**
     * Список для хранения активных ордеров
     */
    private final List<Trade> activeOrders = new ArrayList<>();

    @Autowired
    public WaveTrader(HistoricalDataCollector hdc, OrderExecutor oe, RiskManager rm, BybitClient bc, List<StrategyManager> sms, OrderMonitorService oms) {
        this.historicalDataCollector = hdc;
        this.orderExecutor = oe;
        this.riskManager = rm;
        this.strategyManagers = sms;
        this.bybitClient = bc;
        this.orderMonitorService = oms;
    }

    @PostConstruct
    private void init() {
        if (indicatorAnalyzer == null) {
            log.info("Loading....");
            historicalDataCollector.init(symbols, intervals);

            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    log.info("Initialization executed after 1 minute delay");
                    if (!historicalDataCollector.getKlineCache().isEmpty()) {
                        indicatorAnalyzer = new IndicatorAnalyzer(historicalDataCollector.getKlineCache(), symbols);
                        historicalDataCollector.setAnalyser(indicatorAnalyzer);
                        log.info("IndicatorAnalyzer Initialized!");
                    }
                }
            };

            // Запуск задачи с задержкой в 1 минуту (60000 миллисекунд)
            new Timer().schedule(task, 60000);
        }
    }

    /**
     * Основной метод для анализа и торговли.
     * Выполняется с фиксированным интервалом времени.
     */
    @Override
    @Scheduled(fixedRate = 60000)
    public void haunt() {
        if (indicatorAnalyzer == null) {
            log.info("Trader Loading....");
            return;
        }

        // Обновляем баланс перед началом торгового цикла
        refreshBalance();
        orderMonitorService.monitorOrders(activeOrders);

        // Проходим по каждому торговому символу
        for (String symbol : symbols) {
            analyzeSymbol(symbol);
        }
    }

    /**
     * Анализирует данные индикаторов и пин-баров для конкретного символа.
     *
     * @param symbol торговый символ
     */
    private void analyzeSymbol(String symbol) {
        // Анализируем индикаторы
        List<Signal> indicatorsAnalysisResult = analyzeIndicators(symbol);
        // Анализируем пин-бары
        List<PinBarSignal> pinBarAnalysisResult = analyzePinBars(symbol);
        // Анализируем стратегии
        List<TradeSignal> signals = analyzeStrategy(indicatorsAnalysisResult, pinBarAnalysisResult);

        if (signals.isEmpty()) {
            return;
        }

        log.info("------------------------------- Signals for symbol: " + symbol + " -----------------------------------------------/");

        // Оцениваем риски для выставления сделки
        Map<TradeSignal, RiskEvaluation> riskEvaluations = evaluateRisk(signals, indicatorsAnalysisResult);
        log.info("Risk Evaluation results for {}: {}", symbol, riskEvaluations);

        // Обрабатываем результаты оценки рисков
        processRiskEvaluations(riskEvaluations);
    }

    /**
     * Выполняет анализ индикаторов для указанного символа.
     *
     * @param symbol торговый символ
     * @return список сигналов индикаторов
     */
    private List<Signal> analyzeIndicators(String symbol) {
        List<Signal> indicatorsAnalysisResult = indicatorAnalyzer.analyze(symbol, intervals);
        LogUtils.logAnalysis(indicatorsAnalysisResult);
        return indicatorsAnalysisResult;
    }

    /**
     * Выполняет анализ пин-баров для указанного символа.
     *
     * @param symbol торговый символ
     * @return список сигналов пин-баров
     */
    private List<PinBarSignal> analyzePinBars(String symbol) {
        List<PinBarSignal> pinBarAnalysisResult = PinBarDetector.analyze(symbol, intervals, historicalDataCollector.getKlineCache());
        LogUtils.logPinBarSignals(pinBarAnalysisResult);
        return pinBarAnalysisResult;
    }

    /**
     * Выполняет анализ стратегий на основе результатов анализа индикаторов и пин-баров.
     *
     * @param indicatorsAnalysisResult результаты анализа индикаторов
     * @param pinBarAnalysisResult     результаты анализа пин-баров
     * @return список торговых сигналов
     */
    private List<TradeSignal> analyzeStrategy(List<Signal> indicatorsAnalysisResult, List<PinBarSignal> pinBarAnalysisResult) {
        return strategyManagers.stream()
                .map(manager -> manager.analyzeData(indicatorsAnalysisResult, pinBarAnalysisResult))
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(tradeSignal -> !AnalysisResult.HOLD.equals(tradeSignal.getSignalType()))
                .collect(Collectors.toList());
    }

    /**
     * Выполняет оценку рисков для списка торговых сигналов.
     *
     * @param signals                  список торговых сигналов
     * @param indicatorsAnalysisResult результаты анализа индикаторов
     * @return карта с торговыми сигналами и их оценкой рисков
     */
    private Map<TradeSignal, RiskEvaluation> evaluateRisk(List<TradeSignal> signals, List<Signal> indicatorsAnalysisResult) {
        return riskManager.evaluateRisk(signals, indicatorsAnalysisResult, balance);
    }

    /**
     * Обрабатывает результаты оценки рисков для торговых сигналов.
     *
     * @param riskEvaluations карта с торговыми сигналами и их оценкой рисков
     */
    private void processRiskEvaluations(Map<TradeSignal, RiskEvaluation> riskEvaluations) {
        riskEvaluations.forEach((signal, evaluation) -> {
            if (RiskEvaluation.ACCEPTABLE.equals(evaluation)) {
                // Обрабатываем сигнал с приемлемым риском
                processTradeSignal(signal, evaluation);
            } else {
                log.info("Risk for {} is not Acceptable, no trade", signal.getSymbol());
            }
        });
        log.info("---------------------------------------------------------------------------------------/");
    }

    /**
     * Обрабатывает торговый сигнал с приемлемым риском.
     *
     * @param signal     торговый сигнал
     * @param evaluation оценка риска
     */
    private void processTradeSignal(TradeSignal signal, RiskEvaluation evaluation) {
        // Логируем информацию о сигнале
        LogUtils.logSignalInfo(signal, evaluation);
        List<TradeSignal> list = List.of(signal);
        LogUtils.logTradeSignals(list);
        LogUtils.logTradeSignalsToFile(list);

        // Проверяем баланс перед исполнением сделки
        if (balance.doubleValue() < 5) {
            log.info("Balance is less than 5 USDT, current: {}. Return", balance);
            return;
        }

        // Оцениваем и подготавливаем сделку
        Trade trade = riskManager.evaluateAndPrepareTrade(signal, evaluation, balance);
        log.info("RiskManager give trade {}", trade);

        if (trade != null) {
            log.warn("Risk for is Acceptable, executing trade: {}", trade);
            executeTrade(trade);
        }
    }

    /**
     * Исполняет торговую сделку.
     *
     * @param trade торговая сделка
     */
    private void executeTrade(Trade trade) {
        try {
            orderExecutor.executeOrder(trade).thenAccept(orderId -> {
                if (orderId != null) {
                    trade.setOrderId(orderId);
                    log.info("Trade executed: {}", trade);
                } else {
                    log.error("Order ID is null for trade: {}", trade);
                }

                // Обновляем баланс и добавляем сделку в список активных ордеров
                balance = bybitClient.getBalance();
                activeOrders.add(trade);

                // Сохранение сделки в файл
                LogUtils.logTradeToFile(trade);
            }).exceptionally(ex -> {
                log.error("Failed to execute trade: {}", trade, ex);
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to execute trade: {}", trade, e);
        }
    }

    private void refreshBalance() {
        if (checkCounter == 0 || checkCounter++ >= REQUEST_INTERVAL) {
            balance = bybitClient.getBalance();
            checkCounter = 1; // Сброс счетчика после обновления баланса

            log.info("Balance: {}", balance);
        }
    }

}
