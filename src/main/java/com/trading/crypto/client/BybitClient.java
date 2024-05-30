package com.trading.crypto.client;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.account.AccountType;
import com.bybit.api.client.domain.account.request.AccountDataRequest;
import com.bybit.api.client.restApi.BybitApiAccountRestClient;
import com.bybit.api.client.service.BybitApiClientFactory;
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

    private final BybitApiAccountRestClient bybitApiRestClient;
    private final String apiKey;
    private final String apiSecret;

    /**
     * Конструктор BybitClient. Инициализирует API клиент Bybit.
     *
     * @param apiKey    API ключ для доступа к Bybit API.
     * @param apiSecret Секретный ключ для доступа к Bybit API.
     */
    public BybitClient(@Value("${bybit.api.key}") String apiKey, @Value("${bybit.api.secret}") String apiSecret) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;

        if (apiKey == null || apiKey.isEmpty() || apiSecret == null || apiSecret.isEmpty()) {
            this.bybitApiRestClient = null;
        } else {
            var factory = BybitApiClientFactory.newInstance(apiKey, apiSecret, BybitApiConfig.MAINNET_DOMAIN, false);
            this.bybitApiRestClient = factory.newAccountRestClient();
        }
    }

    /**
     * Получает текущий баланс аккаунта в USDT с Bybit.
     * https://bybit-exchange.github.io/docs/v5/account/wallet-balance
     *
     * @return Баланс аккаунта в USDT.
     */
    public BigDecimal getBalance() {
        if (apiKey == null || apiKey.isEmpty() || apiSecret == null || apiSecret.isEmpty()) {
            return BigDecimal.valueOf(100);
        }

        try {
            AccountDataRequest request = AccountDataRequest.builder()
                    .accountType(AccountType.UNIFIED)
                    .baseCoin("USDT")
                    .build();

            Map<String, Object> response = (Map<String, Object>) bybitApiRestClient.getWalletBalance(request);

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
}
