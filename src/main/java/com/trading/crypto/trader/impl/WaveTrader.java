package com.trading.crypto.trader.impl;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.analyzer.impl.IndicatorAnalyzer;
import com.trading.crypto.data.impl.HistoricalDataCollector;
import com.trading.crypto.data.impl.RealTimeDataStreamer;
import com.trading.crypto.manager.RiskManager;
import com.trading.crypto.manager.StrategyManager;
import com.trading.crypto.model.RiskEvaluation;
import com.trading.crypto.model.TradeSignal;
import com.trading.crypto.order.OrderExecutor;
import com.trading.crypto.trader.Trader;
import com.trading.crypto.util.LogUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WaveTrader implements Trader {

    public static final String symbol = "BTCUSDT";
    private final List<MarketInterval> intervals = List.of(MarketInterval.ONE_MINUTE, MarketInterval.FIVE_MINUTES, MarketInterval.HOURLY);

    private final HistoricalDataCollector historicalDataCollector;
    private final RealTimeDataStreamer realTimeDataStreamer;
    private IndicatorAnalyzer indicatorAnalyzer;
    private final OrderExecutor orderExecutor;
    private final RiskManager riskManager;
    private final List<StrategyManager> strategyManagers;

    @Autowired
    public WaveTrader(HistoricalDataCollector hdc, RealTimeDataStreamer rtds, OrderExecutor oe, RiskManager rm, List<StrategyManager> sms) {
        this.historicalDataCollector = hdc;
        this.realTimeDataStreamer = rtds;
        this.orderExecutor = oe;
        this.riskManager = rm;
        this.strategyManagers = sms;
    }

    @PostConstruct
    private void init(){
        if (indicatorAnalyzer == null) {
            log.info("Loading....");
            historicalDataCollector.init(symbol, intervals);

            Timer timer = new Timer();
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    log.info("Initialization executed after 1 minute delay");
                    if (historicalDataCollector.getKlineCache().size() != 0)
                    {
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
            log.info("Loading....");
            return;
        }

        // Анализ данных индикаторов
        var indicatorsAnalysisResult = indicatorAnalyzer.calculateIndicators(intervals);
        LogUtils.logAnalysis(symbol, indicatorsAnalysisResult);

        // Анализ рынка стратегией возможно ИИ
        List<TradeSignal> signals = strategyManagers.stream()
                .map(manager -> manager.analyzeData(indicatorsAnalysisResult))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        LogUtils.logTradeSignals(signals);

        // Оценка рисков для выставления сделки
        var riskEvaluation = riskManager.evaluateRisk(signals, indicatorsAnalysisResult);
        //log.info("riskEvaluation result: {}", signals);

        // принятие решения на основании полученных данных
        if (riskEvaluation == RiskEvaluation.ACCEPTABLE) {
            //riskManager.evaluateR
//            switch (indicatorsAnalysisResult) {
//                case BUY:
//                    break;
//                case SELL:
//                    break;
//                case STRONG_BUY:
//                    break;
//                case STRONG_SELL:
//                    break;
//                case HOLD:
//                    break;
//            }
            log.warn("Risk is Acceptable, can trade!!!");
        }
    }


}
