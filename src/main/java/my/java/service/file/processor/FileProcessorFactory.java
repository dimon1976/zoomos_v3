// src/main/java/my/java/service/file/processor/FileProcessorFactory.java
package my.java.service.file.processor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Фабрика для создания подходящего процессора файлов.
 */
@Component
@Slf4j
public class FileProcessorFactory {

    private final List<FileProcessor> processors;

    @Autowired
    public FileProcessorFactory(List<FileProcessor> processors) {
        this.processors = processors;
        log.info("Зарегистрировано {} процессоров файлов", processors.size());
    }

    /**
     * Создает подходящий процессор для указанного файла.
     */
    public Optional<FileProcessor> createProcessor(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            log.error("Файл не существует или не указан путь");
            return Optional.empty();
        }

        String fileName = filePath.getFileName().toString().toLowerCase();
        String extension = getFileExtension(fileName);
        String mimeType = detectMimeType(filePath);

        // Приоритет поиска:
        // 1. Прямое совпадение по CanProcess
        // 2. Совпадение по MIME-типу
        // 3. Совпадение по расширению

        // Проверяем все процессоры на совместимость
        for (FileProcessor processor : processors) {
            if (processor.canProcess(filePath)) {
                return Optional.of(processor);
            }
        }

        // Поиск по MIME-типу
        if (mimeType != null) {
            for (FileProcessor processor : processors) {
                for (String supportedMimeType : processor.getSupportedMimeTypes()) {
                    if (mimeType.equalsIgnoreCase(supportedMimeType)) {
                        return Optional.of(processor);
                    }
                }
            }
        }

        // Поиск по расширению
        if (extension != null) {
            for (FileProcessor processor : processors) {
                for (String supportedExtension : processor.getSupportedFileExtensions()) {
                    if (extension.equalsIgnoreCase(supportedExtension)) {
                        return Optional.of(processor);
                    }
                }
            }
        }

        log.warn("Не найден подходящий процессор для файла: {}", fileName);
        return Optional.empty();
    }

    /**
     * Получает список всех поддерживаемых расширений файлов.
     */
    public Set<String> getSupportedFileExtensions() {
        Set<String> extensions = new HashSet<>();
        processors.forEach(processor -> {
            for (String ext : processor.getSupportedFileExtensions()) {
                extensions.add(ext.toLowerCase());
            }
        });
        return extensions;
    }

    /**
     * Получает список всех поддерживаемых MIME-типов.
     */
    public Set<String> getSupportedMimeTypes() {
        Set<String> mimeTypes = new HashSet<>();
        processors.forEach(processor -> {
            for (String mimeType : processor.getSupportedMimeTypes()) {
                mimeTypes.add(mimeType.toLowerCase());
            }
        });
        return mimeTypes;
    }

    /**
     * Извлекает расширение из имени файла.
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1) : "";
    }

    /**
     * Определяет MIME-тип файла.
     */
    private String detectMimeType(Path filePath) {
        try {
            return Files.probeContentType(filePath);
        } catch (Exception e) {
            log.warn("Не удалось определить MIME-тип файла");
            return null;
        }
    }
}