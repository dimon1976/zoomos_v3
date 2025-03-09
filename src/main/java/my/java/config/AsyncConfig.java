package my.java.config;

import lombok.extern.slf4j.Slf4j;
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

    /**
     * Создает пул потоков для асинхронной обработки файлов
     *
     * @return настроенный TaskExecutor
     */
    @Bean(name = "fileProcessingExecutor")
    public TaskExecutor fileProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Базовый размер пула (сколько потоков создается сразу)
        executor.setCorePoolSize(2);

        // Максимальный размер пула (сколько потоков может быть в пике)
        executor.setMaxPoolSize(4);

        // Размер очереди задач
        executor.setQueueCapacity(10);

        // Префикс для имен потоков
        executor.setThreadNamePrefix("file-proc-");

        // Политика отклонения задач при переполнении пула и очереди
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Разрешаем завершение потоков при завершении приложения
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Время ожидания завершения задач при завершении приложения (в секундах)
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("Initialized file processing thread pool: coreSize={}, maxSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * Создает пул потоков для операций с меньшим приоритетом (например, для сбора статистики)
     *
     * @return настроенный TaskExecutor
     */
    @Bean(name = "statsExecutor")
    public TaskExecutor statsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("stats-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.initialize();

        return executor;
    }
}