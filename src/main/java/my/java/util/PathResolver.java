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
 * Утилитный класс для работы с путями файлов.
 * Предоставляет методы для сохранения и управления файлами в различных директориях.
 */
@Component
@Slf4j
public class PathResolver {

    private static final String FILE_EXT_SEPARATOR = ".";
    private static final String PATH_SEPARATOR = "/";
    private static final int BYTES_IN_KB = 1024;
    private static final int BYTES_IN_MB = 1024 * 1024;
    private static final int BYTES_IN_GB = 1024 * 1024 * 1024;

    @Value("${app.file.temp-dir:./temp-files}")
    private String tempFileDir;

    @Value("${app.file.upload-dir:./uploads}")
    private String uploadFileDir;

    /**
     * Получает путь к директории временных файлов.
     *
     * @return путь к директории временных файлов
     */
    public Path getTempDirectory() {
        Path tempDir = Paths.get(tempFileDir);
        ensureDirectoryExists(tempDir);
        return tempDir;
    }

    /**
     * Получает путь к директории загрузок.
     *
     * @return путь к директории загрузок
     */
    public Path getUploadDirectory() {
        Path uploadDir = Paths.get(uploadFileDir);
        ensureDirectoryExists(uploadDir);
        return uploadDir;
    }

    /**
     * Создает директорию, если она не существует.
     *
     * @param directory путь к директории
     * @throws RuntimeException если не удалось создать директорию
     */
    public void ensureDirectoryExists(Path directory) {
        try {
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
                log.info("Создана директория: {}", directory);
            }
        } catch (IOException e) {
            log.error("Ошибка при создании директории {}: {}", directory, e.getMessage());
            throw new RuntimeException("Не удалось создать директорию: " + directory, e);
        }
    }

    /**
     * Сохраняет файл во временную директорию.
     *
     * @param file файл для сохранения
     * @param prefix префикс для имени файла
     * @return путь к сохраненному файлу
     * @throws IOException если произошла ошибка при сохранении файла
     */
    public Path saveToTempFile(MultipartFile file, String prefix) throws IOException {
        Path tempDir = getTempDirectory();
        return saveFile(file, tempDir, prefix);
    }

    /**
     * Сохраняет файл в директорию загрузок.
     *
     * @param file файл для сохранения
     * @param prefix префикс для имени файла
     * @return путь к сохраненному файлу
     * @throws IOException если произошла ошибка при сохранении файла
     */
    public Path saveToUploadDirectory(MultipartFile file, String prefix) throws IOException {
        Path uploadDir = getUploadDirectory();
        return saveFile(file, uploadDir, prefix);
    }

    /**
     * Сохраняет файл в указанную директорию.
     *
     * @param file файл для сохранения
     * @param directory директория для сохранения
     * @param prefix префикс для имени файла
     * @return путь к сохраненному файлу
     * @throws IOException если произошла ошибка при сохранении файла
     */
    private Path saveFile(MultipartFile file, Path directory, String prefix) throws IOException {
        String originalFilename = getOriginalFilename(file);
        String extension = getFileExtension(originalFilename);
        String fileName = generateUniqueFileName(prefix, extension);

        Path filePath = directory.resolve(fileName);

        // Сохраняем файл
        Files.write(filePath, file.getBytes());

        log.debug("Файл сохранен в: {}", filePath);
        return filePath;
    }

    /**
     * Получает оригинальное имя файла или имя по умолчанию, если оно отсутствует.
     *
     * @param file загруженный файл
     * @return оригинальное имя файла или имя по умолчанию
     */
    private String getOriginalFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        return originalFilename != null ? originalFilename : "file.tmp";
    }

    /**
     * Генерирует уникальное имя файла с указанным префиксом и расширением.
     *
     * @param prefix префикс для имени файла
     * @param extension расширение файла
     * @return уникальное имя файла
     */
    private String generateUniqueFileName(String prefix, String extension) {
        return prefix + "_" + UUID.randomUUID().toString() + FILE_EXT_SEPARATOR + extension;
    }

    /**
     * Копирует файл из временной директории в директорию загрузок.
     *
     * @param tempFilePath путь к временному файлу
     * @param prefix префикс для имени файла
     * @return путь к сохраненному файлу
     * @throws IOException если произошла ошибка при копировании файла
     */
    public Path moveFromTempToUpload(Path tempFilePath, String prefix) throws IOException {
        Path uploadDir = getUploadDirectory();
        String extension = getFileExtension(tempFilePath.toString());
        String fileName = generateUniqueFileName(prefix, extension);

        Path targetPath = uploadDir.resolve(fileName);

        // Копируем файл
        Files.copy(tempFilePath, targetPath);

        // Удаляем временный файл
        deleteFileWithLogging(tempFilePath);

        log.debug("Файл перемещен из временной директории в директорию загрузок: {} -> {}", tempFilePath, targetPath);
        return targetPath;
    }

    /**
     * Удаляет файл с логированием результата.
     *
     * @param filePath путь к файлу
     * @return true, если файл успешно удален
     */
    private boolean deleteFileWithLogging(Path filePath) {
        try {
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.debug("Файл удален: {}", filePath);
            } else {
                log.warn("Файл не найден для удаления: {}", filePath);
            }
            return deleted;
        } catch (IOException e) {
            log.error("Ошибка при удалении файла {}: {}", filePath, e.getMessage());
            return false;
        }
    }

    /**
     * Удаляет файл.
     *
     * @param filePath путь к файлу
     * @return true, если файл успешно удален
     */
    public boolean deleteFile(Path filePath) {
        return deleteFileWithLogging(filePath);
    }

    /**
     * Получает размер файла.
     *
     * @param filePath путь к файлу
     * @return размер файла в байтах или -1, если файл не существует
     */
    public long getFileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (IOException e) {
            log.error("Ошибка при получении размера файла {}: {}", filePath, e.getMessage());
            return -1;
        }
    }

    /**
     * Получает расширение файла из имени файла.
     *
     * @param filename имя файла
     * @return расширение файла или пустая строка, если расширение отсутствует
     */
    public String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf(FILE_EXT_SEPARATOR);
        if (lastDotIndex > 0) {
            return filename.substring(lastDotIndex + 1);
        }
        return "";
    }

    /**
     * Создает абсолютный путь из относительного.
     *
     * @param relativePath относительный путь
     * @return абсолютный путь или null, если входной путь null или пустой
     */
    public Path resolveRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }

        Path path = Paths.get(relativePath);
        if (path.isAbsolute()) {
            return path;
        }

        // Проверяем различные варианты путей
        if (isPathInDir(relativePath, "temp-files")) {
            return resolvePathInTempDir(relativePath);
        } else if (isPathInDir(relativePath, "uploads")) {
            return resolvePathInUploadDir(relativePath);
        }

        // Пробуем найти в обеих директориях
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
     * Проверяет, начинается ли путь с указанной директории.
     *
     * @param path путь для проверки
     * @param dirName имя директории
     * @return true, если путь начинается с указанной директории
     */
    private boolean isPathInDir(String path, String dirName) {
        return path.startsWith(dirName + PATH_SEPARATOR) ||
                path.startsWith(dirName + "\\");
    }

    /**
     * Разрешает путь во временной директории.
     *
     * @param path относительный путь
     * @return абсолютный путь во временной директории
     */
    private Path resolvePathInTempDir(String path) {
        String subPath = path.substring(path.indexOf(PATH_SEPARATOR) + 1);
        return getTempDirectory().resolve(subPath);
    }

    /**
     * Разрешает путь в директории загрузок.
     *
     * @param path относительный путь
     * @return абсолютный путь в директории загрузок
     */
    private Path resolvePathInUploadDir(String path) {
        String subPath = path.substring(path.indexOf(PATH_SEPARATOR) + 1);
        return getUploadDirectory().resolve(subPath);
    }

    /**
     * Форматирует размер файла в читаемый вид.
     *
     * @param size размер файла в байтах
     * @return форматированный размер файла
     */
    public String formatFileSize(long size) {
        if (size < BYTES_IN_KB) {
            return size + " B";
        } else if (size < BYTES_IN_MB) {
            return String.format("%.2f KB", size / (double)BYTES_IN_KB);
        } else if (size < BYTES_IN_GB) {
            return String.format("%.2f MB", size / (double)BYTES_IN_MB);
        } else {
            return String.format("%.2f GB", size / (double)BYTES_IN_GB);
        }
    }
}