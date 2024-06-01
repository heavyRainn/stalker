package com.trading.crypto.client;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.TradeOrderType;
import com.bybit.api.client.domain.account.AccountType;
import com.bybit.api.client.domain.account.request.AccountDataRequest;
import com.bybit.api.client.domain.market.request.MarketDataRequest;
import com.bybit.api.client.domain.trade.Side;
import com.bybit.api.client.domain.trade.TimeInForce;
import com.bybit.api.client.domain.trade.request.TradeOrderRequest;
import com.bybit.api.client.restApi.BybitApiAccountRestClient;
import com.bybit.api.client.restApi.BybitApiAsyncTradeRestClient;
import com.bybit.api.client.restApi.BybitApiCallback;
import com.bybit.api.client.restApi.BybitApiMarketRestClient;
import com.bybit.api.client.service.BybitApiClientFactory;
import com.trading.crypto.model.Trade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * BybitClient - класс для взаимодействия с Bybit API для получения данных аккаунта.
 * Использует библиотеку bybit-java-api.
 */
@Slf4j
@Component
public class BybitClient {

    private final BybitApiAccountRestClient apiRestClient;
    private final BybitApiAsyncTradeRestClient tradeRestClient;
    private final BybitApiMarketRestClient marketRestClient;

    /**
     * Конструктор BybitClient. Инициализирует API клиент Bybit.
     *
     * @param apiKey    API ключ для доступа к Bybit API.
     * @param apiSecret Секретный ключ для доступа к Bybit API.
     */
    public BybitClient(@Value("${bybit.api.key}") String apiKey, @Value("${bybit.api.secret}") String apiSecret) {

        if (apiKey == null || apiKey.isEmpty() || apiSecret == null || apiSecret.isEmpty()) {
            this.apiRestClient = null;
            this.tradeRestClient = null;
            this.marketRestClient = null;
        } else {
            var factory = BybitApiClientFactory.newInstance(apiKey, apiSecret, BybitApiConfig.MAINNET_DOMAIN, false);
            this.apiRestClient = factory.newAccountRestClient();
            this.tradeRestClient = factory.newAsyncTradeRestClient();
            this.marketRestClient = factory.newMarketDataRestClient();
        }
    }

    /**
     * Получает текущий баланс аккаунта в USDT с Bybit.
     * https://bybit-exchange.github.io/docs/v5/account/wallet-balance
     *
     * @return Баланс аккаунта в USDT.
     */
    public BigDecimal getBalance() {
        if (apiRestClient == null) {
            return BigDecimal.valueOf(100);
        }
        try {
            AccountDataRequest request = AccountDataRequest.builder()
                    .accountType(AccountType.UNIFIED)
                    .baseCoin("USDT")
                    .build();

            Map<String, Object> response = (Map<String, Object>) apiRestClient.getWalletBalance(request);

            if (response != null && response.containsKey("result")) {
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("list");
                if (list != null) {
                    for (Map<String, Object> balanceInfo : list) {
                        List<Map<String, Object>> coins = (List) balanceInfo.get("coin");
                        for (Map<String, Object> map: coins) {
                            if ("USDT".equals(map.get("coin"))) {
                                return new BigDecimal((String) map.get("equity"));
                            }
                        }
                    }
                }
            }
            return BigDecimal.valueOf(100);
        } catch (Exception e) {
            log.error("Exception while fetching balance info USDT", e);
            return BigDecimal.valueOf(100);
        }
    }

    /**
     * Получает текущую цену для указанного символа.
     *
     * @param symbol Символ для которого нужно получить текущую цену.
     * @return Текущая цена символа.
     */
    public BigDecimal getCurrentPrice(String symbol) {
        if (marketRestClient == null) {
            log.error("MarketRestClient is not initialized.");
            return BigDecimal.valueOf(-1);
        }

        try {
            MarketDataRequest request = MarketDataRequest.builder()
                    .symbol(symbol)
                    .category(CategoryType.LINEAR)
                    .build();

            Object response = marketRestClient.getMarketTickers(request);
            if (response instanceof Map) {
                Map<String, Object> responseMap = (Map<String, Object>) response;
                if (responseMap.containsKey("result")) {
                    Map<String, Object> resultMap = (Map<String, Object>) responseMap.get("result");
                    if (resultMap.containsKey("list")) {
                        List<Map<String, Object>> list = (List<Map<String, Object>>) resultMap.get("list");
                        if (!list.isEmpty()) {
                            Map<String, Object> tickerData = list.get(0);
                            if (tickerData.containsKey("lastPrice")) {
                                return new BigDecimal(tickerData.get("lastPrice").toString());
                            }
                        }
                    }
                }
            }
            return BigDecimal.valueOf(-1);
        } catch (Exception e) {
            log.error("Exception while fetching current price for symbol: {}", symbol, e);
            return BigDecimal.valueOf(-1);
        }
    }

    public boolean placeLimitOrder(Trade trade) {
        try {
            TradeOrderRequest orderRequest = createTradeOrderRequest(trade);
//            tradeRestClient.createOrder(orderRequest, new BybitApiCallback<>() {
//                @Override
//                public void onResponse(Object response) {
//                    log.info("Order placed successfully: {}", response);
//                }
//
//                @Override
//                public void onFailure(Throwable throwable) {
//                    log.error("Failed to place order for symbol: {}", trade.getSymbol(), throwable);
//                }
//            });
            return true;
        } catch (Exception e) {
            log.error("Exception while placing limit order for symbol: {}", trade.getSymbol(), e);
            return false;
        }
    }

    public static TradeOrderRequest createTradeOrderRequest(Trade trade) {
        return TradeOrderRequest.builder()
                .category(CategoryType.LINEAR)
                .symbol(trade.getSymbol())
                .side(trade.getSide())
                .orderType(TradeOrderType.LIMIT)
                .qty(String.valueOf(trade.getAmount()))
                .price(String.valueOf(trade.getEntryPrice()))
                .timeInForce(TimeInForce.GTC)
                .takeProfit(String.valueOf(trade.getTakeProfit()))
                .stopLoss(String.valueOf(trade.getStopLoss()))
                .build();
    }
}
