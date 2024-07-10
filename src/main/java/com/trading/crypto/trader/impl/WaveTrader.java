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

    public static final List<String> symbols = List.of("FTMUSDT", "AVAXUSDT", "ADAUSDT", "GMTUSDT", "DOTUSDT", "1INCHUSDT", "NEARUSDT", "TONUSDT");
    private final List<MarketInterval> intervals = List.of(MarketInterval.ONE_MINUTE);

    private final HistoricalDataCollector historicalDataCollector;
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

    @Autowired
    public WaveTrader(HistoricalDataCollector hdc, OrderExecutor oe, RiskManager rm, BybitClient bc, List<StrategyManager> sms) {
        this.historicalDataCollector = hdc;
        this.orderExecutor = oe;
        this.riskManager = rm;
        this.strategyManagers = sms;
        this.bybitClient = bc;
    }

    @PostConstruct
    private void init() {
        if (indicatorAnalyzer == null) {
            log.info("Loading....");
            historicalDataCollector.init(symbols, intervals);
            balance = bybitClient.getBalance();

            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    log.info("Initialization executed after 1 minute delay");
                    if (!historicalDataCollector.getKlineCache().isEmpty()) {
                        indicatorAnalyzer = new IndicatorAnalyzer(historicalDataCollector.getKlineCache(), symbols);
                        historicalDataCollector.setAnalyser(indicatorAnalyzer);
                        log.info("IndicatorAnalyzer Initialized!");
                    }
                    log.info("Loaded! Balance:{}", balance);
                }
            };

            // Запуск задачи с задержкой в 1 минуту (60000 миллисекунд)
            new Timer().schedule(task, 60000);
        }
    }

    @Override
    @Scheduled(fixedRate = 60000)
    public void haunt() {
        if (indicatorAnalyzer == null) {
            log.info("Trader Loading....");
            return;
        }

        refreshValance();

        log.info("Balance: {}", balance);

        for (String symbol : symbols) {
            // Анализ данных индикаторов для конкретного символа
            List<Signal> indicatorsAnalysisResult = indicatorAnalyzer.analyze(symbol, intervals);
            LogUtils.logAnalysis(indicatorsAnalysisResult);

            // Анализ пин-баров для конкретного символа
            List<PinBarSignal> pinBarAnalysisResult = PinBarDetector.analyze(symbol, intervals, historicalDataCollector.getKlineCache());
            LogUtils.logPinBarSignals(pinBarAnalysisResult);

            // Анализ рынка стратегией возможно ИИ для конкретного символа
            List<TradeSignal> signals = strategyManagers.stream()
                    .map(manager -> manager.analyzeData(indicatorsAnalysisResult, pinBarAnalysisResult))
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .filter(tradeSignal -> !AnalysisResult.HOLD.equals(tradeSignal.getSignalType()))
                    .collect(Collectors.toList());

            if (signals.isEmpty()) {
                log.info("Haunting....");
                refreshValance();
                continue;
            }

            log.info("------------------------------- Signals for symbol: " + symbol + " -----------------------------------------------/");
            //LogUtils.logTradeSignals(signals);
            //LogUtils.logTradeSignalsToFile(signals);

            // Оценка рисков для выставления сделки для конкретного символа
            Map<TradeSignal, RiskEvaluation> riskEvaluations = riskManager.evaluateRisk(signals, indicatorsAnalysisResult, balance);
            log.info("Risk Evaluation results for {}: {}", symbol, riskEvaluations);

            riskEvaluations.forEach((signal, evaluation) -> {
                if (RiskEvaluation.ACCEPTABLE.equals(evaluation)) {
                    // Логируем информацию о сигнале
                    LogUtils.logSignalInfo(signal, evaluation);
                    List<TradeSignal> list = List.of(signal);
                    LogUtils.logTradeSignals(list);
                    LogUtils.logTradeSignalsToFile(list);

                    if (balance.doubleValue() < 5) {
                        log.info("Balance is less than 5 USDT, current: {}. Return", balance);
                        return;
                    }

                    Trade trade = riskManager.evaluateAndPrepareTrade(signal, evaluation, balance);
                    log.info("RiskManager give trade {}", trade);

                    if (trade != null) {
                        log.warn("Risk for is Acceptable, executing trade: {}", trade);
                        try {
                            orderExecutor.executeOrder(trade);
                            log.info("Trade executed: {}", trade);

                            // Сохранение сделки в файл
                            LogUtils.logTradeToFile(trade);

                            balance = bybitClient.getBalance();
                        } catch (Exception e) {
                            log.error("Failed to execute trade: {}", trade, e);
                        }
                    }
                } else {
                    log.info("Risk for {} is not Acceptable, no trade", signal.getSymbol());
                }
            });
            log.info("---------------------------------------------------------------------------------------/");
        }
    }

    private void refreshValance() {
        if (checkCounter == 0 || checkCounter++ >= REQUEST_INTERVAL) {
            balance = bybitClient.getBalance();
            checkCounter = 1; // Сброс счетчика после обновления баланса
        }
    }
    
}
