// src/main/java/my/java/util/PathResolver.java (предполагаемый путь)
package my.java.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Утилита для работы с путями файлов
 */
@Component
@Slf4j
public class PathResolver {

    @Value("${application.upload.dir:uploads}")
    private String uploadDir;

    @Value("${application.temp.dir:temp}")
    private String tempDir;

    /**
     * Сохраняет загруженный файл во временную директорию
     */
    public Path saveToTempFile(MultipartFile file, String prefix) throws IOException {
        // Создаем временную директорию, если не существует
        Path tempDirPath = Paths.get(tempDir);
        Files.createDirectories(tempDirPath);

        // Генерируем уникальное имя файла
        String originalFilename = file.getOriginalFilename();
        String filename = prefix + "_" + UUID.randomUUID() +
                (originalFilename != null ? getFileExtension(originalFilename) : "");

        Path tempFile = tempDirPath.resolve(filename);

        // Сохраняем файл
        Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
        return tempFile;
    }

    /**
     * Перемещает файл из временной директории в постоянную
     */
    public Path moveFromTempToUpload(Path tempFilePath, String prefix) throws IOException {
        // Создаем директорию для загрузок, если не существует
        Path uploadDirPath = Paths.get(uploadDir);
        Files.createDirectories(uploadDirPath);

        // Генерируем уникальное имя файла
        String filename = prefix + "_" + UUID.randomUUID() +
                getFileExtension(tempFilePath.getFileName().toString());

        Path targetPath = uploadDirPath.resolve(filename);

        // Перемещаем файл
        Files.move(tempFilePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        return targetPath;
    }

    /**
     * Удаляет файл
     */
    public void deleteFile(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Не удалось удалить файл {}: {}", filePath, e.getMessage());
        }
    }

    /**
     * Получает размер файла
     */
    public long getFileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (IOException e) {
            log.warn("Не удалось получить размер файла {}: {}", filePath, e.getMessage());
            return 0;
        }
    }

    /**
     * Получает расширение файла
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex) : "";
    }
}