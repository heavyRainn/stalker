package com.trading.crypto.core;

import com.trading.crypto.trader.Trader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class Core implements ApplicationRunner {

    @Autowired
    private Trader trader;

    @Override
    public void run(ApplicationArguments args) {
        
    }
}
