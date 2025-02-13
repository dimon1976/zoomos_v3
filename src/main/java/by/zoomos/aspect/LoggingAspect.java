package by.zoomos.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.Arrays;

/**
 * Аспект для логирования операций
 */
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    /**
     * Поинткат для всех сервисных методов
     */
    @Pointcut("within(@org.springframework.stereotype.Service *)")
    public void servicePointcut() {}

    /**
     * Поинткат для всех контроллеров
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void controllerPointcut() {}

    /**
     * Поинткат для всех методов обработки файлов
     */
    @Pointcut("execution(* by.zoomos.service.file.processor.*.*(..))")
    public void fileProcessingPointcut() {}

    /**
     * Логирование всех сервисных методов
     */
    @Around("servicePointcut()")
    public Object logServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethod(joinPoint, "Service");
    }

    /**
     * Логирование всех методов контроллера
     */
    @Around("controllerPointcut()")
    public Object logControllerMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethod(joinPoint, "Controller");
    }

    /**
     * Логирование методов обработки файлов
     */
    @Around("fileProcessingPointcut()")
    public Object logFileProcessing(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethod(joinPoint, "FileProcessing");
    }

    /**
     * Основной метод логирования
     */
    private Object logMethod(ProceedingJoinPoint joinPoint, String type) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getDeclaringType().getSimpleName() + "." + signature.getName();

        log.debug("[{}] Начало выполнения метода: {}", type, methodName);
        log.trace("[{}] Параметры метода: {}", type, Arrays.toString(joinPoint.getArgs()));

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        try {
            Object result = joinPoint.proceed();
            stopWatch.stop();

            log.debug("[{}] Успешное выполнение метода: {}. Время выполнения: {} мс",
                    type, methodName, stopWatch.getTotalTimeMillis());
            log.trace("[{}] Результат метода: {}", type, result);

            return result;
        } catch (Throwable e) {
            stopWatch.stop();
            log.error("[{}] Ошибка в методе: {}. Время выполнения: {} мс",
                    type, methodName, stopWatch.getTotalTimeMillis(), e);
            throw e;
        }
    }
}
