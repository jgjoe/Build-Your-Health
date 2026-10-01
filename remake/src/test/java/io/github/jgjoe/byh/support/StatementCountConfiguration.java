package io.github.jgjoe.byh.support;

import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Installs {@link CountingDataSource} around the auto-configured datasource so tests can
 * measure how many statements a request executes. Registered as a bean post-processor so the
 * datasource keeps being created (and pooled) by Spring Boot.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StatementCountConfiguration {

    @Bean
    static BeanPostProcessor countingDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource && !(bean instanceof CountingDataSource)) {
                    return new CountingDataSource(dataSource);
                }
                return bean;
            }
        };
    }
}
