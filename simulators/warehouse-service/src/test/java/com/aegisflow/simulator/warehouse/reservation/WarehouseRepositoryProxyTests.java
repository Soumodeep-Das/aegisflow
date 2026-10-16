package com.aegisflow.simulator.warehouse.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;

class WarehouseRepositoryProxyTests {
    @Test
    void actualRepositoryBeansSupportSpringExceptionTranslationProxiesWithoutADatabase() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("postgres");
            context.registerBean(PersistenceExceptionTranslationPostProcessor.class, () -> {
                var postProcessor = new PersistenceExceptionTranslationPostProcessor();
                postProcessor.setProxyTargetClass(true);
                return postProcessor;
            });
            context.registerBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));
            context.registerBean(EntityManager.class, () -> mock(EntityManager.class));
            context.register(IdempotencyRepository.class, OutboxEventRepository.class,
                    ReservationRepository.class, ReservationLineRepository.class);
            context.refresh();

            for (var type : List.of(IdempotencyRepository.class, OutboxEventRepository.class,
                    ReservationRepository.class, ReservationLineRepository.class)) {
                assertThat(AopUtils.isCglibProxy(context.getBean(type)))
                        .as("%s must support repository exception translation", type.getSimpleName())
                        .isTrue();
            }
        }
    }
}
