package by.zoomos.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Конфигурация асинхронной обработки
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    /**
     * Создает пул потоков для асинхронной обработки файлов
     *
     * @return настроенный executor
     */
    @Bean(name = "fileProcessingExecutor")
    public Executor fileProcessingExecutor() {
        log.info("Создание пула потоков для обработки файлов");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("FileProcessor-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // Настройка обработчика отклоненных задач
        executor.setRejectedExecutionHandler((r, e) -> {
            log.error("Задача отклонена из-за переполнения очереди");
            throw new RuntimeException("Система перегружена, попробуйте позже");
        });

        executor.initialize();
        return executor;
    }

    /**
     * Создает пул потоков для экспорта данных
     *
     * @return настроенный executor
     */
    @Bean(name = "exportExecutor")
    public Executor exportExecutor() {
        log.info("Создание пула потоков для экспорта данных");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("Exporter-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.setRejectedExecutionHandler((r, e) -> {
            log.error("Задача экспорта отклонена из-за переполнения очереди");
            throw new RuntimeException("Сервис экспорта перегружен, попробуйте позже");
        });

        executor.initialize();
        return executor;
    }
}