package com.trading.crypto.client;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.TradeOrderType;
import com.bybit.api.client.domain.asset.request.AssetDataRequest;
import com.bybit.api.client.domain.market.MarketInterval;
import com.bybit.api.client.domain.market.request.MarketDataRequest;
import com.bybit.api.client.domain.position.request.PositionDataRequest;
import com.bybit.api.client.domain.trade.PositionIdx;
import com.bybit.api.client.domain.trade.Side;
import com.bybit.api.client.domain.trade.TimeInForce;
import com.bybit.api.client.domain.trade.request.TradeOrderRequest;
import com.bybit.api.client.domain.websocket_message.public_channel.KlineData;
import com.bybit.api.client.domain.websocket_message.public_channel.WebSocketKlineMessage;
import com.bybit.api.client.domain.websocket_message.public_channel.WebSocketTickerMessage;
import com.bybit.api.client.domain.websocket_message.public_channel.WebsocketOrderbookMessage;
import com.bybit.api.client.service.BybitApiClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BybitWebSocketClient {

    /**
     * Push frequency: Derivatives & Options - 100ms, Spot - real-time
     *
     * Ticker data
     * 1. symbol: Символ тикера, например "BTCUSD" для Bitcoin в долларах США.
     * 2.  tickDirection: Направление последнего изменения в цене.
     * 3.  bidPrice: Цена предложения - сколько покупатель готов заплатить.
     * 4.  bidSize: Размер текущего предложения.
     * 5.  bidIv: Неявная волатильность предложения (Bid Implied Volatility).
     * 6.  askPrice: Цена продажи - цена, по которой продавец намерен продать актив.
     * 7.  askSize: Размер текущего запроса на продажу.
     * 8.  askIv: Неявная волатильность запроса (Ask Implied Volatility).
     * 9.  lastPrice: Последняя цена сделки.
     * 10. highPrice24h: Максимальная цена в течение последних 24 часов.
     * 11. lowPrice24h: Минимальная цена в течение последних 24 часов.
     * 12. prevPrice24h: Предыдущая цена 24 часа назад.
     * 13. volume24h: Объем торгов в течение последних 24 часов.
     * 14. turnover24h: Оборот за последние 24 часа.
     * 15. price24hPcnt: Процентное изменение цены за последние 24 часа.
     * 16. usdIndexPrice: Цена индекса в USD.
     * 17. underlyingPrice: Цена базового актива.
     * 18. markPrice: Цена маркировки.
     * 19. indexPrice: Цена индекса.
     * 20. markPriceIv: Неявная волатильность цены маркировки.
     * 21. openInterest: Открытый интерес (количество открытых контрактов на актив).
     * 22. openInterestValue: Общая стоимость открытого интереса на актив.
     * 23. totalVolume: Общий объем торгов.
     * 24. totalTurnover: Общий оборот.
     * 25. nextFundingTime: Время следующего финансирования.
     * 26. fundingRate: Ставка финансирования.
     * 27. bid1Price: Цена первого уровня предложения.
     * 28. bid1Size: Размер первого уровня предложения.
     * 29. ask1Price: Цена первого уровня запроса.
     * 30. ask1Size: Размер первого уровня запроса.
     * 31. delta: Дельта – чувствительность цены опциона к изменению цены актива.
     * 32. gamma: Гамма - чувствительность дельты к изменению цены актива.
     * 33. vega: Вега - чувствительность цены опциона к изменению волатильности актива.
     * 34. theta: Тета - чувствительность цены опциона к изменению времени до исполнения.
     * 35. deliveryTime: Время поставки.
     * 36. basisRate: Базовая ставка.
     * 37. deliveryFeeRate: Ставка пошлины за поставку.
     * 38. predictedDeliveryPrice: Прогнозируемая цена поставки.
     * 39. change24h: Изменение цены за последние 24 часа.
     *
     * Topic:
     * tickers.{symbol}
     * @param symbol ticker of crypto pair
     */
    public void subscribeTicker(String symbol) {
        var client = BybitApiClientFactory.newInstance(BybitApiConfig.STREAM_MAINNET_DOMAIN, true).newWebsocketClient(20);

        client.setMessageHandler(message -> {
            var tickerData = (new ObjectMapper()).readValue(message, WebSocketTickerMessage.class);
            // Process message data here
            log.info("Ticker {}, data: {}",symbol, tickerData.getData().toString());
        });

        // Ticker
        client.getPublicChannelStream(List.of("tickers." + symbol), BybitApiConfig.V5_PUBLIC_LINEAR);
    }

    /**
     * Available intervals:
     *
     *     1 3 5 15 30 (min)
     *     60 120 240 360 720 (min)
     *     D (day)
     *     W (week)
     *     M (month)
     *
     * Push frequency: 1-60s
     *
     * Topic:
     * kline.{interval}.{symbol} e.g., kline.30.BTCUSDT
     * @param symbol
     */
    public void subscribeKline(String symbol, String interval) {
        var client = BybitApiClientFactory.newInstance(BybitApiConfig.STREAM_MAINNET_DOMAIN, true).newWebsocketClient(20);

        client.setMessageHandler(message -> {
            var klineData = (new ObjectMapper()).readValue(message, WebSocketKlineMessage.class);
            List<KlineData> data = klineData.getData();

            // Process message data here
            log.info("Kline {}, data: {}", symbol, data.toString());
        });

        // Ticker
        client.getPublicChannelStream(List.of("kline." + interval + "." + symbol), BybitApiConfig.V5_PUBLIC_LINEAR);
    }

    /**
     * Linear & inverse:
     * Level 1 data, push frequency: 10ms
     * Level 50 data, push frequency: 20ms
     * Level 200 data, push frequency: 100ms
     * Level 500 data, push frequency: 100ms
     *
     * Spot:
     * Level 1 data, push frequency: 10ms
     * Level 50 data, push frequency: 20ms
     * Level 200 data, push frequency: 200ms
     *
     * Option:
     * Level 25 data, push frequency: 20ms
     * Level 100 data, push frequency: 100ms
     *
     * Topic:
     * orderbook.{depth}.{symbol} e.g., orderbook.1.BTCUSDT
     *
     * @param symbol
     */
    public void subscribeOrderBook(String symbol, String level) {
        var client = BybitApiClientFactory.newInstance(BybitApiConfig.STREAM_TESTNET_DOMAIN, true, "okhttp3").newWebsocketClient(20);

        client.setMessageHandler(message -> {
            var orderBookMessage = (new ObjectMapper()).readValue(message, WebsocketOrderbookMessage.class);

            // Process message data here
            log.info("Order book {}: {}", symbol, orderBookMessage.getData().toString());
        });

        // Orderbook
        client.getPublicChannelStream(List.of("orderbook."+ level + "." + symbol), BybitApiConfig.V5_PUBLIC_LINEAR);

        // Subscribe Orderbook more than one args
        //client.getPublicChannelStream(List.of("orderbook.50.BTCUSDT","orderbook.1.ETHUSDT"), BybitApiConfig.V5_PUBLIC_LINEAR);
    }

    public void subscribeMarketData(String symbol) {
        log.info("subscribeMarketData, symbol: {}", symbol);
        var client = BybitApiClientFactory.newInstance(BybitApiConfig.TESTNET_DOMAIN,true).newMarketDataRestClient();
        var marketKLineRequest = MarketDataRequest.builder().category(CategoryType.LINEAR).symbol(symbol).marketInterval(MarketInterval.WEEKLY).build();

        // Weekly market Kline
        var marketKlineResult = client.getMarketLinesData(marketKLineRequest);
        log.info("subscribeMarketData"+ marketKlineResult.toString());

        // Weekly market price Kline for a symbol
        var marketPriceKlineResult = client.getMarketPriceLinesData(marketKLineRequest);
        log.info("subscribeMarketData"+ marketPriceKlineResult.toString());

        // Weekly index price Kline for a symbol
        var indexPriceKlineResult = client.getIndexPriceLinesData(marketKLineRequest);
        log.info("subscribeMarketData"+ indexPriceKlineResult.toString());

        // Weekly premium index price Kline for a symbol
        var indexPremiumPriceKlineResult = client.getPremiumIndexPriceLinesData(marketKLineRequest);
        log.info("subscribeMarketData"+ indexPremiumPriceKlineResult.toString());

        // Get server time
        var serverTime = client.getServerTime();
        log.info("subscribeMarketData"+ serverTime.toString());
    }

    /**
     * https://bybit-exchange.github.io/docs/v5/order/create-order
     * @param symbol
     * @param quantity
     */
    public void placeSingleOrder(String symbol, String quantity){
        var client = BybitApiClientFactory.newInstance("YOUR_API_KEY", "YOUR_API_SECRET", BybitApiConfig.TESTNET_DOMAIN, true).newAsyncTradeRestClient();
        var newOrderRequest = TradeOrderRequest.builder()
                .category(CategoryType.LINEAR).symbol(symbol)
                .side(Side.BUY)
                .orderType(TradeOrderType.MARKET)
                .qty(quantity)
                .timeInForce(TimeInForce.GOOD_TILL_CANCEL)
                .positionIdx(PositionIdx.ONE_WAY_MODE).build();

        client.createOrder(newOrderRequest, System.out::println);
    }

    public void getPositionInfo(String symbol){
        var client = BybitApiClientFactory.newInstance("YOUR_API_KEY", "YOUR_API_SECRET", BybitApiConfig.TESTNET_DOMAIN).newAsyncPositionRestClient();
        var positionListRequest = PositionDataRequest.builder().category(CategoryType.LINEAR).symbol(symbol).build();
        client.getPositionInfo(positionListRequest, System.out::println);
    }

    public void getAssetInfo(){
        var client = BybitApiClientFactory.newInstance("YOUR_API_KEY", "YOUR_API_SECRET", BybitApiConfig.TESTNET_DOMAIN).newAsyncAssetRestClient();
        var coinExchangeRecordsRequest = AssetDataRequest.builder().build();
        client.getAssetCoinExchangeRecords(coinExchangeRecordsRequest, System.out::println);
    }

    private void positionSubscribe(){
        var client = BybitApiClientFactory.newInstance("YOUR_API_KEY", "YOUR_API_SECRET", BybitApiConfig.STREAM_TESTNET_DOMAIN).newWebsocketClient();

        // Position
        client.getPrivateChannelStream(List.of("position"), BybitApiConfig.V5_PRIVATE);
    }

    private void createOrder(){
        var client = BybitApiClientFactory.newInstance("YOUR_API_KEY", "YOUR_API_SECRET", BybitApiConfig.TESTNET_DOMAIN, true).newAsyncTradeRestClient();
        Map<String, Object> order =Map.of(
                "category", "option",
                "symbol", "BTC-29DEC23-10000-P",
                "side", "Buy",
                "orderType", "Limit",
                "orderIv", "0.1",
                "qty", "0.1",
                "price", "5"
        );
        client.createOrder(order, System.out::println);
    }

}
