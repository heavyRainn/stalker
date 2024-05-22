package com.trading.crypto.manager;

import com.trading.crypto.model.PinBarSignal;
import com.trading.crypto.model.Signal;
import com.trading.crypto.model.TradeSignal;

import java.util.List;

public interface StrategyManager {

    List<TradeSignal> analyzeData(List<Signal> indicatorsAnalysisResult, List<PinBarSignal> pinBarAnalysisResult);
}
