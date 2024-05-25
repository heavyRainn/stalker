package com.trading.crypto.client;

import com.bybit.api.client.config.BybitApiConfig;
import com.bybit.api.client.domain.account.request.AccountDataRequest;
import com.bybit.api.client.restApi.BybitApiAccountRestClient;
import com.bybit.api.client.service.BybitApiClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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

        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            this.bybitApiRestClient = null;
        } else {
            var factory = BybitApiClientFactory.newInstance(apiKey, apiSecret, BybitApiConfig.TESTNET_DOMAIN, true);
            this.bybitApiRestClient = factory.newAccountRestClient();
        }
    }

    /**
     * Получает текущий баланс аккаунта с Bybit.
     *
     * @return Баланс аккаунта.
     */
    public BigDecimal getBalance() {
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            return BigDecimal.valueOf(100);
        }

        try {
            AccountDataRequest request = AccountDataRequest.builder().build();
            Map<String, Object> response = (Map<String, Object>) bybitApiRestClient.getWalletBalance(request);
            // Пример обработки ответа:
            // {
            //   "ret_code": 0,
            //   "ret_msg": "OK",
            //   "ext_code": "",
            //   "result": {
            //     "USDT": {
            //       "equity": 1.234
            //     }
            //   },
            //   "ext_info": null,
            //   "time_now": "1577487745.356362",
            //   "rate_limit_status": 119,
            //   "rate_limit_reset_ms": 1577487745377,
            //   "rate_limit": 120
            // }
            Map<String, Object> result = (Map<String, Object>) response.get("result");
            if (result != null && result.containsKey("USDT")) {
                Map<String, Object> usdtBalance = (Map<String, Object>) result.get("USDT");
                return new BigDecimal(usdtBalance.get("equity").toString());
            } else {
                return new BigDecimal(100);
            }
        } catch (Exception e) {
            log.error("Exception while fetching balance info USDT");
            return new BigDecimal(100);
        }
    }
}
