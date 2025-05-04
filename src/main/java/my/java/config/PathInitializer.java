// src/main/java/my/java/config/PathInitializer.java
package my.java.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.util.PathResolver;
import org.springframework.stereotype.Component;

/**
 * Компонент для инициализации директорий при запуске приложения
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PathInitializer {

    private final PathResolver pathResolver;

    @PostConstruct
    public void init() {
        log.info("Инициализация директорий для файлов...");
        pathResolver.init();
    }
}