package ru.vkr.materials.service;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.*;

@Configuration
public class InventoryTimeConfiguration {
    @Bean
    public Clock inventoryClock() {
        return Clock.system(ZoneId.of("Europe/Moscow"));
    }
}
