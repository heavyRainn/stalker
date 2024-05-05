package com.trading.crypto.ai;

import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerMinMaxScaler;
import org.nd4j.linalg.factory.Nd4j;

import java.io.File;
import java.io.IOException;

public class CryptoPredictionModel {
    private MultiLayerNetwork model; // Модель глубокого обучения
    private NormalizerMinMaxScaler scaler; // Нормализатор данных

    /**
     * Конструктор класса CryptoPredictionModel из файла
     *
     * @param modelFilePath - путь к файлу с моделью
     * @throws IOException - исключение при загрузке модели
     */
    public CryptoPredictionModel(String modelFilePath) throws IOException {
        model = MultiLayerNetwork.load(new File(modelFilePath), true);
        scaler = new NormalizerMinMaxScaler();
    }

    /**
     * Конструктор класса CryptoPredictionModel из существующей модели
     *
     * @param model  - модель глубокого обучения
     * @param scaler - нормализатор данных
     */
    public CryptoPredictionModel(MultiLayerNetwork model, NormalizerMinMaxScaler scaler) {
        this.model = model;
        this.scaler = scaler;
    }

    /**
     * Прогнозирование цены на основе данных
     *
     * @param data - данные для прогнозирования (формат: [timeSeriesLength, numFeatures])
     * @return прогноз цены
     */
    public double predict(double[][] data) {
        // Преобразование данных к формату, ожидаемому моделью
        int numFeatures = data[0].length;
        int timeSeriesLength = data.length;
        double[][][] reshapedData = new double[1][timeSeriesLength][numFeatures];

        for (int i = 0; i < timeSeriesLength; i++) {
            System.arraycopy(data[i], 0, reshapedData[0][i], 0, numFeatures);
        }

        // Создание датасета с метками
        DataSet dataSet = new DataSet(Nd4j.create(reshapedData), Nd4j.create(new double[]{0}));

        // Нормализуем данные
        scaler.fit(dataSet);
        scaler.transform(dataSet);

        // Прогнозирование модели
        return model.output(dataSet.getFeatures(), false).getDouble(0);
    }

    /**
     * Переобучение модели
     *
     * @param data   - данные для обучения (формат: [numRows, timeSeriesLength, numFeatures])
     * @param labels - метки (labels) для обучения
     */
    public void fit(double[][] data, double[] labels) {
        int numFeatures = data[0].length;
        int numRows = data.length;
        double[][][] reshapedData = new double[numRows][1][numFeatures];

        for (int i = 0; i < numRows; i++) {
            System.arraycopy(data[i], 0, reshapedData[i][0], 0, numFeatures);
        }

        DataSet dataSet = new DataSet(Nd4j.create(reshapedData), Nd4j.create(labels));

        // Нормализуем данные
        scaler.fit(dataSet);
        scaler.transform(dataSet);

        // Обучаем модель
        model.fit(dataSet);
    }

    /**
     * Сохранение модели в файл
     *
     * @param filePath - путь к файлу
     * @throws IOException - исключение при сохранении модели
     */
    public void save(String filePath) throws IOException {
        model.save(new File(filePath));
    }
}
