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
 * Выбирает процессор на основе типа и содержимого файла.
 */
@Component
@Slf4j
public class FileProcessorFactory {

    private final List<FileProcessor> processors;

    /**
     * Создает фабрику с указанным списком процессоров.
     *
     * @param processors список доступных процессоров
     */
    @Autowired
    public FileProcessorFactory(List<FileProcessor> processors) {
        this.processors = processors;
        logAvailableProcessors();
    }

    /**
     * Логирует информацию о доступных процессорах.
     */
    private void logAvailableProcessors() {
        log.info("Зарегистрировано {} процессоров файлов:", processors.size());
        for (FileProcessor processor : processors) {
            log.info("Процессор: {}, поддерживаемые расширения: {}",
                    processor.getClass().getSimpleName(),
                    String.join(", ", processor.getSupportedFileExtensions()));
        }
    }

    /**
     * Создает подходящий процессор для указанного файла.
     *
     * @param filePath путь к файлу
     * @return подходящий процессор или пустой Optional, если процессор не найден
     */
    public Optional<FileProcessor> createProcessor(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            log.error("Файл не существует или не указан путь: {}", filePath);
            return Optional.empty();
        }

        String fileName = filePath.getFileName().toString().toLowerCase();
        String extension = getFileExtension(fileName);
        String mimeType = detectMimeType(filePath);

        log.debug("Поиск процессора для файла: {}, расширение: {}, MIME-тип: {}", fileName, extension, mimeType);

        // Приоритет поиска:
        // 1. Проверяем canProcess()
        // 2. Ищем по MIME-типу, если он определен
        // 3. Ищем по расширению файла

        // Сначала проверяем все процессоры на совместимость
        for (FileProcessor processor : processors) {
            if (processor.canProcess(filePath)) {
                log.debug("Найден подходящий процессор по совместимости: {}", processor.getClass().getSimpleName());
                return Optional.of(processor);
            }
        }

        // Ищем по MIME-типу, если он определен
        if (mimeType != null && !mimeType.isEmpty()) {
            for (FileProcessor processor : processors) {
                for (String supportedMimeType : processor.getSupportedMimeTypes()) {
                    if (mimeType.equalsIgnoreCase(supportedMimeType)) {
                        log.debug("Найден подходящий процессор по MIME-типу: {}", processor.getClass().getSimpleName());
                        return Optional.of(processor);
                    }
                }
            }
        }

        // Ищем по расширению файла
        if (extension != null && !extension.isEmpty()) {
            for (FileProcessor processor : processors) {
                for (String supportedExtension : processor.getSupportedFileExtensions()) {
                    if (extension.equalsIgnoreCase(supportedExtension)) {
                        log.debug("Найден подходящий процессор по расширению: {}", processor.getClass().getSimpleName());
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
     *
     * @return множество поддерживаемых расширений
     */
    public Set<String> getSupportedFileExtensions() {
        Set<String> extensions = new HashSet<>();
        for (FileProcessor processor : processors) {
            for (String ext : processor.getSupportedFileExtensions()) {
                extensions.add(ext.toLowerCase());
            }
        }
        return extensions;
    }

    /**
     * Получает список всех поддерживаемых MIME-типов.
     *
     * @return множество поддерживаемых MIME-типов
     */
    public Set<String> getSupportedMimeTypes() {
        Set<String> mimeTypes = new HashSet<>();
        for (FileProcessor processor : processors) {
            for (String mimeType : processor.getSupportedMimeTypes()) {
                mimeTypes.add(mimeType.toLowerCase());
            }
        }
        return mimeTypes;
    }

    /**
     * Проверяет, поддерживается ли расширение файла.
     *
     * @param extension расширение файла
     * @return true, если расширение поддерживается
     */
    public boolean isExtensionSupported(String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }

        extension = extension.toLowerCase().trim();
        for (FileProcessor processor : processors) {
            for (String supportedExtension : processor.getSupportedFileExtensions()) {
                if (extension.equalsIgnoreCase(supportedExtension)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Проверяет, поддерживается ли MIME-тип.
     *
     * @param mimeType MIME-тип
     * @return true, если MIME-тип поддерживается
     */
    public boolean isMimeTypeSupported(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return false;
        }

        mimeType = mimeType.toLowerCase().trim();
        for (FileProcessor processor : processors) {
            for (String supportedMimeType : processor.getSupportedMimeTypes()) {
                if (mimeType.equalsIgnoreCase(supportedMimeType)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Извлекает расширение из имени файла.
     *
     * @param fileName имя файла
     * @return расширение файла или пустая строка
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }

    /**
     * Определяет MIME-тип файла.
     *
     * @param filePath путь к файлу
     * @return MIME-тип или null, если не удалось определить
     */
    private String detectMimeType(Path filePath) {
        try {
            return Files.probeContentType(filePath);
        } catch (Exception e) {
            log.warn("Не удалось определить MIME-тип файла: {}", e.getMessage());
            return null;
        }
    }
}