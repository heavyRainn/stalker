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

    public static final String symbol = "BTCUSDT";
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
            historicalDataCollector.init(symbol, intervals);

            Timer timer = new Timer();
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    log.info("Initialization executed after 1 minute delay");
                    if (!historicalDataCollector.getKlineCache().isEmpty()) {
                        indicatorAnalyzer = new IndicatorAnalyzer(historicalDataCollector.getKlineCache(), symbol);
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
            log.info(symbol + " Trader Loading....");
            return;
        }

        // Анализ данных индикаторов
        Map<MarketInterval, Signal> indicatorsAnalysisResult = indicatorAnalyzer.analyze(intervals);
        LogUtils.logAnalysis(indicatorsAnalysisResult);

        Map<MarketInterval, PinBarAnalysisResult> pinBarAnalysisResult = PinBarDetector.analyze(intervals, historicalDataCollector.getKlineCache());
        LogUtils.logPinBarAnalysis(pinBarAnalysisResult);

        // Анализ рынка стратегией возможно ИИ
        List<TradeSignal> signals = strategyManagers.stream()
                .map(manager -> manager.analyzeData(indicatorsAnalysisResult))
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(tradeSignal -> !AnalysisResult.HOLD.equals(tradeSignal.getSignalType()))
                .collect(Collectors.toList());

        if (signals.isEmpty()) {
            log.info("Haunting....");
            return;
        }

        log.info("------------------------------- Signals -----------------------------------------------/");
        LogUtils.logTradeSignals(signals);
        LogUtils.logTradeSignalsToFile(signals);

        // Оценка рисков для выставления сделки
        Map<TradeSignal, RiskEvaluation> riskEvaluations = riskManager.evaluateRisk(signals, indicatorsAnalysisResult);
        log.info("Risk Evaluation results: {}", riskEvaluations);

        riskEvaluations.forEach((signal, evaluation) -> {
            if (RiskEvaluation.ACCEPTABLE.equals(evaluation)) {
                // Логируем информацию о сигнале
                LogUtils.logSignalInfo(signal, evaluation);

                Trade trade = riskManager.evaluateAndPrepareTrade(signal, evaluation);
                log.warn("Risk for {} is Acceptable, can trade!!!", trade);
            } else {
                log.info("Risk for {} is not Acceptable, no trade", signal.getSymbol());
            }
        });
        log.info("---------------------------------------------------------------------------------------/");
    }


}
