package com.trading.crypto.util;

import com.trading.crypto.model.KlineElement;

import java.util.List;

public class DataPreparationUtils {
    public static double[][] prepareMarketData(List<KlineElement> klineElements) {
        int size = klineElements.size();
        double[][] marketData = new double[size][5];
        for (int i = 0; i < size; i++) {
            KlineElement kline = klineElements.get(i);
            marketData[i][0] = kline.getOpenPrice().doubleValue();
            marketData[i][1] = kline.getHighPrice().doubleValue();
            marketData[i][2] = kline.getLowPrice().doubleValue();
            marketData[i][3] = kline.getClosePrice().doubleValue();
            marketData[i][4] = kline.getVolume().doubleValue();
        }
        return marketData;
    }

    public static double[] prepareLabels(List<KlineElement> klineElements) {
        int size = klineElements.size();
        double[] labels = new double[size];
        for (int i = 0; i < size - 1; i++) {
            labels[i] = klineElements.get(i + 1).getClosePrice().doubleValue();
        }
        labels[size - 1] = klineElements.get(size - 1).getClosePrice().doubleValue(); // последний элемент
        return labels;
    }
}
