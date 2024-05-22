package com.trading.crypto.ai;

import com.bybit.api.client.domain.market.MarketInterval;
import com.trading.crypto.data.impl.HistoricalDataCollector;
import com.trading.crypto.model.AnalysisResult;
import com.trading.crypto.model.KlineElement;
import com.trading.crypto.model.Signal;
import com.trading.crypto.model.TradeSignal;
import com.trading.crypto.util.DataPreparationUtils;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerMinMaxScaler;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
//@Component
public class AIStrategyManager  {
    private static final String MODEL_FILE = "StalkerLSTMModel.zip";
    private static final int SEQ_LENGTH = 200; // Количество баров для прогнозирования
    private CryptoPredictionModel predictionModel; // Модель ИИ для прогнозирования
    private final HistoricalDataCollector dataCollector; // Исторический сборщик данных

    /**
     * Конструктор класса AIStrategyManager
     *
     * @param dataCollector - экземпляр сборщика исторических данных
     */
    public AIStrategyManager(HistoricalDataCollector dataCollector) {
        this.dataCollector = dataCollector;
        File modelFile = new File(MODEL_FILE);
        if (modelFile.exists()) {
            try {
                // Загрузка существующей модели из файла
                predictionModel = new CryptoPredictionModel(MODEL_FILE);
                log.info("Loaded existing AI model from {}", MODEL_FILE);
            } catch (IOException e) {
                log.error("Error loading AI model: ", e);
            }
        } else {
            // Создание новой модели и сохранение ее в файл
            try {
                predictionModel = createAndSaveInitialModel();
            } catch (IOException e) {
                log.error("Error loading AI model: ", e);
            }
        }
    }

    /**
     * Анализ данных индикаторов и генерация торгового сигнала
     *
     * @param indicatorsAnalysisResult - карта результатов анализа индикаторов
     * @return торговый сигнал
     */
    //@Override
    public List<TradeSignal> analyzeData(List<Signal> indicatorsAnalysisResult) {
        // Получаем прогноз цены от модели ИИ
        double prediction = getAIPrediction("BTCUSDT", MarketInterval.ONE_MINUTE);

        // Генерация торгового сигнала на основе прогнозов ИИ и результатов индикаторов
        //TradeSignal signal = generateTradeSignal(indicatorsAnalysisResult, prediction, "BTCUSDT");
        //log.info("Generated Trade Signal: {}", signal);
        //return signal;

        return null;
    }

    /**
     * Получение прогноза цены от модели ИИ
     *
     * @param symbol   - торговый символ (например, BTCUSDT)
     * @param interval - интервал торгов (например, MarketInterval.ONE_HOUR)
     * @return прогноз цены
     */
    private double getAIPrediction(String symbol, MarketInterval interval) {
        // Получаем список элементов Kline для заданного символа и интервала
        List<KlineElement> klineElements = dataCollector.getKlineCache()
                .getOrDefault(symbol, Collections.emptyMap())
                .getOrDefault(interval, Collections.emptyList());

        // Проверяем, достаточно ли данных для прогнозирования
        if (klineElements.size() < SEQ_LENGTH) {
            log.warn("Not enough data available for {} at interval {}", symbol, interval);
            return 0;
        }

        // Преобразуем данные в формат, пригодный для модели ИИ
        double[][] marketData = DataPreparationUtils.prepareMarketData(klineElements);
        return predictionModel.predict(marketData);
    }

    /**
     * Создание торгового сигнала на основе результатов анализа индикаторов и прогнозов ИИ
     *
     * @param indicatorsAnalysisResult - карта результатов анализа индикаторов
     * @param aiPrediction             - прогноз цены от модели ИИ
     * @param symbol                   - торговый символ (например, BTCUSDT)
     * @return торговый сигнал
     */
    private TradeSignal generateTradeSignal(Map<MarketInterval, Signal> indicatorsAnalysisResult, double aiPrediction, String symbol) {
        // Получаем первичный анализ из результатов индикаторов
        Signal signal = indicatorsAnalysisResult.get(MarketInterval.ONE_MINUTE); // Замените на нужный интервал

        // Генерируем и возвращаем торговый сигнал
        return createTradeSignal(signal, symbol, aiPrediction, 0.05, 0.03, 1000, System.currentTimeMillis());
    }

    /**
     * Создание объекта торгового сигнала на основе типа сигнала
     *
     * @param symbol     - торговый символ
     * @param entryPrice - цена входа
     * @param stopLoss   - уровень стоп-лосса
     * @param takeProfit - уровень тейк-профита
     * @param amount     - количество торговых единиц
     * @param timestamp  - время сигнала
     * @return объект торгового сигнала
     */
    private TradeSignal createTradeSignal(Signal signal, String symbol, double entryPrice, double stopLoss, double takeProfit, double amount, long timestamp) {
        return switch (signal.getAnalysisResult()) {
            case STRONG_BUY, BUY ->
                    new TradeSignal(signal.getAnalysisResult(), symbol, entryPrice, stopLoss, takeProfit, amount, timestamp);
            case STRONG_SELL, SELL ->
                    new TradeSignal(signal.getAnalysisResult(), symbol, entryPrice, stopLoss, takeProfit, amount, timestamp);
            default -> new TradeSignal(AnalysisResult.HOLD, symbol, 0, 0, 0, 0, timestamp);
        };
    }

    /**
     * Создание новой модели и сохранение ее в файл
     *
     * @return экземпляр CryptoPredictionModel
     */
    private CryptoPredictionModel createAndSaveInitialModel() throws IOException {
        int numInputFeatures = 5; // Количество признаков
        int numOutput = 1; // Один выход
        int lstmLayerSize = 50; // Размер слоя LSTM

        // Конфигурация модели
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .weightInit(WeightInit.XAVIER)
                .updater(new Adam(0.001))
                .list()
                .layer(new LSTM.Builder()
                        .nIn(numInputFeatures)
                        .nOut(lstmLayerSize)
                        .activation(Activation.TANH)
                        .build())
                .layer(new RnnOutputLayer.Builder(LossFunctions.LossFunction.MSE)
                        .activation(Activation.IDENTITY)
                        .nIn(lstmLayerSize)
                        .nOut(numOutput)
                        .build())
                .build();

        // Создаем модель
        MultiLayerNetwork model = new MultiLayerNetwork(conf);
        model.init();
        model.setListeners(new ScoreIterationListener(20));

        // Сохраняем модель в файл
        try {
            model.save(new File(MODEL_FILE), true);
            log.info("Created and saved initial AI model at {}", MODEL_FILE);
        } catch (IOException e) {
            log.error("Error saving initial AI model: ", e);
        }

        // Возвращаем экземпляр CryptoPredictionModel
        return new CryptoPredictionModel(model, new NormalizerMinMaxScaler());
    }
}