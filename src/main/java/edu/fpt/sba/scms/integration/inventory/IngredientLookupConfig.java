package edu.fpt.sba.scms.integration.inventory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Nối cổng {@link IngredientLookupPort} với adapter mặc định cho tới khi M09 triển khai nguyên liệu. */
@Configuration(proxyBeanMethods = false)
public class IngredientLookupConfig {

    @Bean
    @ConditionalOnMissingBean(IngredientLookupPort.class)
    public IngredientLookupPort ingredientLookupPort() {
        return UnavailableIngredientLookup.INSTANCE;
    }
}
