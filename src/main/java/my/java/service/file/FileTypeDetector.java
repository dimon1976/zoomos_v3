package my.java.service.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.util.PathResolver;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Сервис для определения типа файла и его кодировки
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileTypeDetector {

    private static final Set<String> EXCEL_EXTENSIONS = new HashSet<>(Arrays.asList("xls", "xlsx"));
    private static final Set<String> CSV_EXTENSIONS = new HashSet<>(Arrays.asList("csv", "txt"));

    private final PathResolver pathResolver;

    /**
     * Определяет тип файла по его содержимому
     *
     * @param file файл для анализа
     * @return тип файла (FileType)
     * @throws FileOperationException если тип файла не поддерживается
     */
    public FileType detectFileType(MultipartFile file) throws FileOperationException {
        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new FileOperationException("Имя файла не указано");
        }

        String extension = pathResolver.getFileExtension(originalFilename).toLowerCase();

        if (EXCEL_EXTENSIONS.contains(extension)) {
            return determineExcelType(file);
        } else if (CSV_EXTENSIONS.contains(extension)) {
            return FileType.CSV;
        } else {
            throw new FileOperationException("Неподдерживаемый тип файла: " + extension);
        }
    }

    /**
     * Определяет тип файла по его содержимому из Path
     *
     * @param filePath путь к файлу для анализа
     * @return тип файла (FileType)
     * @throws FileOperationException если тип файла не поддерживается
     */
    public FileType detectFileType(Path filePath) throws FileOperationException {
        String fileName = filePath.getFileName().toString();
        String extension = pathResolver.getFileExtension(fileName).toLowerCase();

        if (EXCEL_EXTENSIONS.contains(extension)) {
            return determineExcelType(filePath);
        } else if (CSV_EXTENSIONS.contains(extension)) {
            return FileType.CSV;
        } else {
            throw new FileOperationException("Неподдерживаемый тип файла: " + extension);
        }
    }

    /**
     * Определяет кодировку текстового файла
     *
     * @param file файл для анализа
     * @return обнаруженная кодировка или UTF-8 по умолчанию
     */
    public Charset detectCharset(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            UniversalDetector detector = new UniversalDetector(null);

            detector.handleData(bytes, 0, Math.min(bytes.length, 8192)); // Проверяем первые 8KB
            detector.dataEnd();

            String encoding = detector.getDetectedCharset();
            detector.reset();

            if (encoding != null) {
                try {
                    return Charset.forName(encoding);
                } catch (Exception e) {
                    log.warn("Detected charset '{}' is not supported, using UTF-8", encoding);
                }
            }
        } catch (Exception e) {
            log.error("Error detecting charset: {}", e.getMessage());
        }

        return StandardCharsets.UTF_8; // По умолчанию используем UTF-8
    }

    /**
     * Определяет кодировку текстового файла по пути
     *
     * @param filePath путь к файлу для анализа
     * @return обнаруженная кодировка или UTF-8 по умолчанию
     */
    public Charset detectCharset(Path filePath) {
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            UniversalDetector detector = new UniversalDetector(null);

            detector.handleData(bytes, 0, Math.min(bytes.length, 8192)); // Проверяем первые 8KB
            detector.dataEnd();

            String encoding = detector.getDetectedCharset();
            detector.reset();

            if (encoding != null) {
                try {
                    return Charset.forName(encoding);
                } catch (Exception e) {
                    log.warn("Detected charset '{}' is not supported, using UTF-8", encoding);
                }
            }
        } catch (Exception e) {
            log.error("Error detecting charset: {}", e.getMessage());
        }

        return StandardCharsets.UTF_8; // По умолчанию используем UTF-8
    }

    /**
     * Определяет тип Excel-файла (XLS или XLSX)
     *
     * @param file Excel-файл
     * @return тип файла
     * @throws FileOperationException если произошла ошибка при определении типа
     */
    private FileType determineExcelType(MultipartFile file) throws FileOperationException {
        try (InputStream is = new BufferedInputStream(file.getInputStream())) {
            if (FileMagic.valueOf(is) == FileMagic.OLE2) {
                return FileType.XLS;
            } else {
                return FileType.XLSX;
            }
        } catch (Exception e) {
            log.error("Error determining Excel file type: {}", e.getMessage());
            throw new FileOperationException("Ошибка при определении типа Excel-файла: " + e.getMessage());
        }
    }

    /**
     * Определяет тип Excel-файла (XLS или XLSX) по пути
     *
     * @param filePath путь к Excel-файлу
     * @return тип файла
     * @throws FileOperationException если произошла ошибка при определении типа
     */
    private FileType determineExcelType(Path filePath) throws FileOperationException {
        try (InputStream is = new BufferedInputStream(Files.newInputStream(filePath))) {
            if (FileMagic.valueOf(is) == FileMagic.OLE2) {
                return FileType.XLS;
            } else {
                return FileType.XLSX;
            }
        } catch (Exception e) {
            log.error("Error determining Excel file type: {}", e.getMessage());
            throw new FileOperationException("Ошибка при определении типа Excel-файла: " + e.getMessage());
        }
    }

    /**
     * Создает объект Workbook на основе типа файла
     *
     * @param file файл для открытия
     * @param fileType тип файла
     * @return объект Workbook
     * @throws IOException если произошла ошибка при открытии файла
     */
    public Workbook createWorkbook(MultipartFile file, FileType fileType) throws IOException {
        InputStream is = file.getInputStream();

        switch (fileType) {
            case XLS:
                return new HSSFWorkbook(new POIFSFileSystem(is));
            case XLSX:
                return new XSSFWorkbook(is);
            default:
                throw new IOException("Неподдерживаемый тип файла для создания Workbook: " + fileType);
        }
    }

    /**
     * Создает объект Workbook на основе типа файла по пути
     *
     * @param filePath путь к файлу для открытия
     * @param fileType тип файла
     * @return объект Workbook
     * @throws IOException если произошла ошибка при открытии файла
     */
    public Workbook createWorkbook(Path filePath, FileType fileType) throws IOException {
        InputStream is = Files.newInputStream(filePath);

        switch (fileType) {
            case XLS:
                return new HSSFWorkbook(new POIFSFileSystem(is));
            case XLSX:
                return new XSSFWorkbook(is);
            default:
                throw new IOException("Неподдерживаемый тип файла для создания Workbook: " + fileType);
        }
    }

    /**
     * Перечисление типов файлов
     */
    public enum FileType {
        CSV,
        XLS,
        XLSX
    }
}