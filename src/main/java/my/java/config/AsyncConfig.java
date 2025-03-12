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

    private static final int FILE_PROCESSING_CORE_POOL_SIZE = 2;
    private static final int FILE_PROCESSING_MAX_POOL_SIZE = 4;
    private static final int FILE_PROCESSING_QUEUE_CAPACITY = 10;
    private static final String FILE_PROCESSING_THREAD_PREFIX = "file-proc-";
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 60;

    private static final int STATS_CORE_POOL_SIZE = 1;
    private static final int STATS_MAX_POOL_SIZE = 2;
    private static final int STATS_QUEUE_CAPACITY = 20;
    private static final String STATS_THREAD_PREFIX = "stats-";

    /**
     * Создает пул потоков для асинхронной обработки файлов
     */
    @Bean(name = "fileProcessingExecutor")
    public TaskExecutor fileProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Настройка размеров пула
        executor.setCorePoolSize(FILE_PROCESSING_CORE_POOL_SIZE);
        executor.setMaxPoolSize(FILE_PROCESSING_MAX_POOL_SIZE);
        executor.setQueueCapacity(FILE_PROCESSING_QUEUE_CAPACITY);
        executor.setThreadNamePrefix(FILE_PROCESSING_THREAD_PREFIX);

        // Политика отклонения задач при переполнении пула и очереди
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Настройка поведения при завершении
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_TIMEOUT_SECONDS);

        executor.initialize();

        log.info("Инициализирован пул обработки файлов: основной размер={}, максимальный размер={}, емкость очереди={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * Создает пул потоков для операций с меньшим приоритетом (например, для сбора статистики)
     */
    @Bean(name = "statsExecutor")
    public TaskExecutor statsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(STATS_CORE_POOL_SIZE);
        executor.setMaxPoolSize(STATS_MAX_POOL_SIZE);
        executor.setQueueCapacity(STATS_QUEUE_CAPACITY);
        executor.setThreadNamePrefix(STATS_THREAD_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.initialize();

        return executor;
    }
}