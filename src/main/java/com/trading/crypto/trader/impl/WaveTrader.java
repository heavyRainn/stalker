package com.trading.crypto.trader.impl;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.analyzer.impl.IndicatorAnalyzer;
import com.trading.crypto.analyzer.impl.PinBarDetector;
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

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WaveTrader implements Trader {

    public static final List<String> symbols = List.of("BTCUSDT", "SOLUSDT");
    private final List<MarketInterval> intervals = List.of(MarketInterval.ONE_MINUTE, MarketInterval.FIVE_MINUTES, MarketInterval.HOURLY);

    private final HistoricalDataCollector historicalDataCollector;
    private IndicatorAnalyzer indicatorAnalyzer;
    private final OrderExecutor orderExecutor;
    private final RiskManager riskManager;
    private final List<StrategyManager> strategyManagers;

    @Autowired
    public WaveTrader(HistoricalDataCollector hdc, OrderExecutor oe, RiskManager rm, List<StrategyManager> sms) {
        this.historicalDataCollector = hdc;
        this.orderExecutor = oe;
        this.riskManager = rm;
        this.strategyManagers = sms;
    }

    @PostConstruct
    private void init() {
        if (indicatorAnalyzer == null) {
            log.info("Loading....");
            historicalDataCollector.init(symbols, intervals);

            Timer timer = new Timer();
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    log.info("Initialization executed after 1 minute delay");
                    if (!historicalDataCollector.getKlineCache().isEmpty()) {
                        indicatorAnalyzer = new IndicatorAnalyzer(historicalDataCollector.getKlineCache(), symbols);
                        historicalDataCollector.setAnalyser(indicatorAnalyzer);
                        log.info("IndicatorAnalyzer Initialized!");
                    }
                    log.info("Loaded!");
                }
            };

            // Запуск задачи с задержкой в 1 минуту (60000 миллисекунд)
            timer.schedule(task, 60000);
        }
    }

    @Override
    @Scheduled(fixedRate = 60000)
    public void haunt() {
        if (indicatorAnalyzer == null) {
            log.info("Trader Loading....");
            return;
        }

        for (String symbol : symbols) {
            // Анализ данных индикаторов для конкретного символа
            List<Signal> indicatorsAnalysisResult = indicatorAnalyzer.analyze(symbol, intervals);
            LogUtils.logAnalysis(indicatorsAnalysisResult);

            // Анализ пин-баров для конкретного символа
            List<PinBarSignal> pinBarAnalysisResult = PinBarDetector.analyze(symbols, intervals, historicalDataCollector.getKlineCache());
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
                continue;
            }

            log.info("------------------------------- Signals for symbol: " + symbol + " -----------------------------------------------/");
            LogUtils.logTradeSignals(signals);
            LogUtils.logTradeSignalsToFile(signals);

            // Оценка рисков для выставления сделки для конкретного символа
            Map<TradeSignal, RiskEvaluation> riskEvaluations = riskManager.evaluateRisk(signals, indicatorsAnalysisResult);
            log.info("Risk Evaluation results for {}: {}", symbol, riskEvaluations);

            riskEvaluations.forEach((signal, evaluation) -> {
                if (RiskEvaluation.ACCEPTABLE.equals(evaluation)) {
                    // Логируем информацию о сигнале
                    LogUtils.logSignalInfo(signal, evaluation);

                    Trade trade = riskManager.evaluateAndPrepareTrade(signal, evaluation);
                    if (trade != null) {
                        log.warn("Risk for {} is Acceptable, executing trade: {}", null, trade);
                        try {
                            //orderExecutor.executeOrder(trade);
                            log.info("Trade executed: {}", trade);
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


}
