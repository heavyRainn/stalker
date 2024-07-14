package com.trading.crypto.client;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.TradeOrderType;
import com.bybit.api.client.domain.account.AccountType;
import com.bybit.api.client.domain.account.request.AccountDataRequest;
import com.bybit.api.client.domain.market.request.MarketDataRequest;
import com.bybit.api.client.domain.position.request.PositionDataRequest;
import com.bybit.api.client.domain.trade.TimeInForce;
import com.bybit.api.client.domain.trade.request.TradeOrderRequest;
import com.bybit.api.client.exception.BybitApiException;
import com.bybit.api.client.restApi.*;
import com.bybit.api.client.service.BybitApiClientFactory;
import com.trading.crypto.model.PositionInfo;
import com.trading.crypto.model.Trade;
import com.trading.crypto.util.StalkerUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
    private final BybitApiPositionRestClient positionRestClient;

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
            this.positionRestClient = null;
        } else {
            var factory = BybitApiClientFactory.newInstance(apiKey, apiSecret, BybitApiConfig.MAINNET_DOMAIN, false);
            this.apiRestClient = factory.newAccountRestClient();
            this.tradeRestClient = factory.newAsyncTradeRestClient();
            this.marketRestClient = factory.newMarketDataRestClient();
            this.positionRestClient = factory.newPositionRestClient();
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
                    .coin("USDT")
                    .build();

            Map<String, Object> response = (Map<String, Object>) apiRestClient.getWalletBalance(request);

            if (response != null && response.containsKey("result")) {
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                List<Map<String, Object>> list = (List<Map<String, Object>>) result.get("list");
                if (list != null) {
                    for (Map<String, Object> balanceInfo : list) {
                        List<Map<String, Object>> coins = (List) balanceInfo.get("coin");
                        for (Map<String, Object> map : coins) {
                            if ("USDT".equals(map.get("coin"))) {
                                return new BigDecimal((String) map.get("availableToWithdraw"));
                            }
                        }
                    }
                }
            }
            return BigDecimal.valueOf(0);
        } catch (Exception e) {
            log.error("Exception while fetching balance info USDT", e);
            return BigDecimal.valueOf(0);
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
                    .baseCoin(symbol)
                    .category(CategoryType.LINEAR)
                    .build();

            Object response = marketRestClient.getMarketTickers(request);
            if (response instanceof Map) {
                Map<String, Object> responseMap = (Map<String, Object>) response;
                if (responseMap.containsKey("result")) {
                    Map<String, Object> resultMap = (Map<String, Object>) responseMap.get("result");
                    if (resultMap.containsKey("list")) {
                        List<Map<String, Object>> list = (List<Map<String, Object>>) resultMap.get("list");
                        for (Map<String, Object> tickerData : list) {
                            if (tickerData.containsKey("symbol") && symbol.equals(tickerData.get("symbol").toString())) {
                                if (tickerData.containsKey("markPrice")) {
                                    return new BigDecimal(tickerData.get("markPrice").toString());
                                }
                            }
                        }
                    }
                }
            }
            return BigDecimal.valueOf(0);
        } catch (Exception e) {
            log.error("Exception while fetching current price for symbol: {}", symbol, e);
            return BigDecimal.valueOf(0);
        }
    }

    public CompletableFuture<String> placeLimitOrder(Trade trade) {
        CompletableFuture<String> futureOrderId = new CompletableFuture<>();
        try {
            TradeOrderRequest orderRequest = createTradeOrderRequest(trade);

            tradeRestClient.createOrder(orderRequest, new BybitApiCallback<>() {
                @Override
                public void onResponse(Object response) {
                    log.info("Order placed successfully: {}", response);

                    // Предполагаем, что response содержит ID ордера
                    if (response instanceof Map) {
                        Map<String, Object> responseMap = (Map<String, Object>) response;
                        if (responseMap.containsKey("result")) {
                            Map<String, Object> result = (Map<String, Object>) responseMap.get("result");
                            String orderId = (String) result.get("orderId");
                            if (orderId != null && !orderId.isEmpty()) {
                                futureOrderId.complete(orderId);
                            } else {
                                log.error("Order ID not found in response");
                                futureOrderId.complete(null);
                            }
                        } else {
                            log.error("Result not found in response");
                            futureOrderId.complete(null);
                        }
                    } else {
                        log.error("Unexpected response format");
                        futureOrderId.complete(null);
                    }
                }

                @Override
                public void onFailure(Throwable throwable) {
                    log.error("Failed to place order for symbol: {}", trade.getSymbol(), throwable);
                    futureOrderId.completeExceptionally(throwable);
                }
            });
        } catch (Exception e) {
            log.error("Exception while placing limit order for symbol: {}", trade.getSymbol(), e);
            futureOrderId.completeExceptionally(e);
        }

        return futureOrderId;
    }

    // https://bybit-exchange.github.io/docs/v5/position
    public PositionInfo getPositionInfoByOrderId(String orderId) {
        if (positionRestClient == null) {
            log.error("PositionRestClient is not initialized.");
            return null;
        }

        try {
            PositionDataRequest request = PositionDataRequest.builder()
                    .orderId(orderId)
                    .category(CategoryType.LINEAR)
                    .build();

            PositionInfo positionInfo = (PositionInfo) positionRestClient.getPositionInfo(request);
            log.info("Position Info: {}", positionInfo);
            return positionInfo;
        } catch (BybitApiException e) {
            log.error("Exception while fetching position info for order ID: {}", orderId, e);
            return null;
        }
    }

    // https://bybit-exchange.github.io/docs/v5/position
    public PositionInfo getPositionsInfo() {
        if (positionRestClient == null) {
            log.error("PositionRestClient is not initialized.");
            return null;
        }

        try {
            PositionDataRequest request = PositionDataRequest.builder()
                    .category(CategoryType.LINEAR)
                    .build();

            PositionInfo positionInfo = (PositionInfo) positionRestClient.getPositionInfo(request);
            log.info("Positions Info: {}", positionInfo);
            return positionInfo;
        } catch (BybitApiException e) {
            log.error("Exception while fetching positions: {}", e);
            return null;
        }
    }

    public static TradeOrderRequest createTradeOrderRequest(Trade trade) {
        return TradeOrderRequest.builder()
                .category(CategoryType.LINEAR)
                .symbol(trade.getSymbol())
                .side(trade.getSide())
                .orderType(TradeOrderType.LIMIT)
                .qty(StalkerUtils.getFormattedAmount(trade.getAmount(), trade.getSymbol()))
                .price(String.valueOf(trade.getEntryPrice()))
                .timeInForce(TimeInForce.GTC)
                .takeProfit(String.valueOf(trade.getTakeProfit()))
                .stopLoss(String.valueOf(trade.getStopLoss()))
                .build();
    }
}
