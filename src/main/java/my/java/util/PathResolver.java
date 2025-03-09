package my.java.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Утилитный класс для работы с путями файлов
 */
@Component
@Slf4j
public class PathResolver {

    @Value("${app.file.temp-dir:./temp-files}")
    private String tempFileDir;

    @Value("${app.file.upload-dir:./uploads}")
    private String uploadFileDir;

    /**
     * Получает путь к директории временных файлов
     *
     * @return путь к директории временных файлов
     */
    public Path getTempDirectory() {
        Path tempDir = Paths.get(tempFileDir);
        ensureDirectoryExists(tempDir);
        return tempDir;
    }

    /**
     * Получает путь к директории загрузок
     *
     * @return путь к директории загрузок
     */
    public Path getUploadDirectory() {
        Path uploadDir = Paths.get(uploadFileDir);
        ensureDirectoryExists(uploadDir);
        return uploadDir;
    }

    /**
     * Создает директорию, если она не существует
     *
     * @param directory путь к директории
     */
    public void ensureDirectoryExists(Path directory) {
        try {
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
                log.info("Created directory: {}", directory);
            }
        } catch (IOException e) {
            log.error("Error creating directory {}: {}", directory, e.getMessage());
            throw new RuntimeException("Не удалось создать директорию: " + directory, e);
        }
    }

    /**
     * Сохраняет файл во временную директорию
     *
     * @param file файл для сохранения
     * @param prefix префикс для имени файла
     * @return путь к сохраненному файлу
     * @throws IOException если произошла ошибка при сохранении файла
     */
    public Path saveToTempFile(MultipartFile file, String prefix) throws IOException {
        Path tempDir = getTempDirectory();

        // Генерируем уникальное имя файла
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename != null ? originalFilename : "file.tmp");
        String tempFileName = prefix + "_" + UUID.randomUUID().toString() + "." + extension;

        Path tempFilePath = tempDir.resolve(tempFileName);

        // Сохраняем файл
        Files.write(tempFilePath, file.getBytes());

        log.debug("File saved to temporary location: {}", tempFilePath);
        return tempFilePath;
    }

    /**
     * Сохраняет файл в директорию загрузок
     *
     * @param file файл для сохранения
     * @param prefix префикс для имени файла
     * @return путь к сохраненному файлу
     * @throws IOException если произошла ошибка при сохранении файла
     */
    public Path saveToUploadDirectory(MultipartFile file, String prefix) throws IOException {
        Path uploadDir = getUploadDirectory();

        // Генерируем уникальное имя файла
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename != null ? originalFilename : "file.tmp");
        String fileName = prefix + "_" + UUID.randomUUID().toString() + "." + extension;

        Path filePath = uploadDir.resolve(fileName);

        // Сохраняем файл
        Files.write(filePath, file.getBytes());

        log.debug("File saved to upload location: {}", filePath);
        return filePath;
    }

    /**
     * Копирует файл из временной директории в директорию загрузок
     *
     * @param tempFilePath путь к временному файлу
     * @param prefix префикс для имени файла
     * @return путь к сохраненному файлу
     * @throws IOException если произошла ошибка при копировании файла
     */
    public Path moveFromTempToUpload(Path tempFilePath, String prefix) throws IOException {
        Path uploadDir = getUploadDirectory();

        // Генерируем уникальное имя файла
        String fileName = prefix + "_" + UUID.randomUUID().toString() + "." + getFileExtension(tempFilePath.toString());

        Path targetPath = uploadDir.resolve(fileName);

        // Копируем файл
        Files.copy(tempFilePath, targetPath);

        // Удаляем временный файл
        try {
            Files.deleteIfExists(tempFilePath);
        } catch (IOException e) {
            log.warn("Could not delete temporary file: {}", tempFilePath);
        }

        log.debug("File moved from temp to upload location: {} -> {}", tempFilePath, targetPath);
        return targetPath;
    }

    /**
     * Удаляет файл
     *
     * @param filePath путь к файлу
     * @return true, если файл успешно удален
     */
    public boolean deleteFile(Path filePath) {
        try {
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.debug("File deleted: {}", filePath);
            } else {
                log.warn("File not found for deletion: {}", filePath);
            }
            return deleted;
        } catch (IOException e) {
            log.error("Error deleting file {}: {}", filePath, e.getMessage());
            return false;
        }
    }

    /**
     * Получает размер файла
     *
     * @param filePath путь к файлу
     * @return размер файла в байтах или -1, если файл не существует
     */
    public long getFileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (IOException e) {
            log.error("Error getting file size {}: {}", filePath, e.getMessage());
            return -1;
        }
    }

    /**
     * Получает расширение файла из имени файла
     *
     * @param filename имя файла
     * @return расширение файла
     */
    public String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf(".");
        if (lastDotIndex > 0) {
            return filename.substring(lastDotIndex + 1);
        }
        return "";
    }

    /**
     * Создает абсолютный путь из относительного
     *
     * @param relativePath относительный путь
     * @return абсолютный путь
     */
    public Path resolveRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }

        Path path = Paths.get(relativePath);
        if (path.isAbsolute()) {
            return path;
        }

        // Если путь начинается с названия директории, пробуем найти его
        if (relativePath.startsWith("temp-files/") || relativePath.startsWith("temp-files\\")) {
            return getTempDirectory().resolve(relativePath.substring("temp-files/".length()));
        } else if (relativePath.startsWith("uploads/") || relativePath.startsWith("uploads\\")) {
            return getUploadDirectory().resolve(relativePath.substring("uploads/".length()));
        }

        // Иначе пробуем найти в обеих директориях
        Path tempPath = getTempDirectory().resolve(relativePath);
        if (Files.exists(tempPath)) {
            return tempPath;
        }

        Path uploadPath = getUploadDirectory().resolve(relativePath);
        if (Files.exists(uploadPath)) {
            return uploadPath;
        }

        // Если не нашли, возвращаем исходный путь
        return path;
    }

    /**
     * Форматирует размер файла в читаемый вид
     *
     * @param size размер файла в байтах
     * @return форматированный размер файла
     */
    public String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
}