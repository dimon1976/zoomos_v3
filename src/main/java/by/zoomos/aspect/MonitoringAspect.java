package by.zoomos.aspect;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Аспект для мониторинга производительности
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class MonitoringAspect {

    private final MeterRegistry meterRegistry;

    /**
     * Поинткат для методов обработки файлов
     */
    @Pointcut("execution(* by.zoomos.service.file.processor.*.*(..))")
    public void fileProcessingPointcut() {}

    /**
     * Поинткат для методов экспорта
     */
    @Pointcut("execution(* by.zoomos.service.export.*.*(..))")
    public void exportPointcut() {}

    /**
     * Мониторинг обработки файлов
     */
    @Around("fileProcessingPointcut()")
    public Object monitorFileProcessing(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        Timer timer = Timer.builder("file.processing")
                .tag("method", methodName)
                .description("Время обработки файла")
                .register(meterRegistry);

        return monitorMethod(joinPoint, timer);
    }

    /**
     * Мониторинг экспорта
     */
    @Around("exportPointcut()")
    public Object monitorExport(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        Timer timer = Timer.builder("file.export")
                .tag("method", methodName)
                .description("Время экспорта данных")
                .register(meterRegistry);

        return monitorMethod(joinPoint, timer);
    }

    /**
     * Основной метод мониторинга
     */
    private Object monitorMethod(ProceedingJoinPoint joinPoint, Timer timer) throws Throwable {
        long startTime = System.nanoTime();

        try {
            Object result = joinPoint.proceed();
            timer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);

            // Увеличиваем счетчик успешных операций
            meterRegistry.counter("operations.success",
                    "class", joinPoint.getSignature().getDeclaringTypeName(),
                    "method", joinPoint.getSignature().getName()
            ).increment();

            return result;
        } catch (Throwable e) {
            // Увеличиваем счетчик ошибок
            meterRegistry.counter("operations.error",
                    "class", joinPoint.getSignature().getDeclaringTypeName(),
                    "method", joinPoint.getSignature().getName(),
                    "exception", e.getClass().getSimpleName()
            ).increment();

            throw e;
        }
    }
}