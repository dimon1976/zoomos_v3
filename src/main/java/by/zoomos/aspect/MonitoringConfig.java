package by.zoomos.aspect;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Конфигурация системы мониторинга
 */
@Configuration
@EnableAspectJAutoProxy
public class MonitoringConfig {

    /**
     * Создает аспект для метрик времени выполнения
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}