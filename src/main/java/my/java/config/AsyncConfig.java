// src/main/java/my/java/config/AsyncConfig.java
package my.java.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Конфигурация асинхронной обработки файлов
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    @Value("${application.async.core-pool-size:2}")
    private int corePoolSize;

    @Value("${application.async.max-pool-size:4}")
    private int maxPoolSize;

    @Value("${application.async.queue-capacity:10}")
    private int queueCapacity;

    /**
     * Создает пул потоков для асинхронной обработки файлов
     */
    @Bean(name = "fileProcessingExecutor")
    public TaskExecutor fileProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Настройки пула из конфигурации
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);

        // Префикс для имен потоков
        executor.setThreadNamePrefix("file-proc-");

        // Политика отклонения задач при переполнении пула и очереди
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Разрешаем завершение потоков при завершении приложения
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Время ожидания завершения задач при завершении приложения (в секундах)
        executor.setAwaitTerminationSeconds(60);

        // Позволяем потокам умирать при бездействии
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);

        executor.initialize();

        log.info("Initialized file processing thread pool: coreSize={}, maxSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * Создает пул потоков для операций с меньшим приоритетом (например, для сбора статистики)
     */
    @Bean(name = "statsExecutor")
    public TaskExecutor statsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("stats-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(300); // 5 минут для статистики
        executor.initialize();

        log.info("Initialized stats thread pool");
        return executor;
    }

    /**
     * Создает пул потоков для уведомлений и легких операций
     */
    @Bean(name = "notificationExecutor")
    public TaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("notify-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(120);
        executor.initialize();

        log.info("Initialized notification thread pool");
        return executor;
    }
}