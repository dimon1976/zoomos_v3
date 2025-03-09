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
    private static final int CHARSET_DETECTION_BUFFER_SIZE = 8192; // 8KB

    private final PathResolver pathResolver;

    /**
     * Перечисление типов файлов
     */
    public enum FileType {
        CSV,
        XLS,
        XLSX
    }

    /**
     * Определяет тип файла по его содержимому и расширению
     *
     * @param file файл для анализа
     * @return тип файла (FileType)
     * @throws FileOperationException если тип файла не поддерживается
     */
    public FileType detectFileType(MultipartFile file) throws FileOperationException {
        String originalFilename = getFilenameOrThrow(file);
        String extension = extractFileExtension(originalFilename);

        return determineFileTypeByExtension(extension);
    }

    /**
     * Получает имя файла или выбрасывает исключение, если оно отсутствует
     *
     * @param file файл для проверки
     * @return имя файла
     * @throws FileOperationException если имя файла не указано
     */
    private String getFilenameOrThrow(MultipartFile file) throws FileOperationException {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isEmpty()) {
            throw new FileOperationException("Имя файла не указано");
        }
        return filename;
    }

    /**
     * Извлекает расширение из имени файла
     *
     * @param filename имя файла
     * @return расширение в нижнем регистре
     */
    private String extractFileExtension(String filename) {
        return pathResolver.getFileExtension(filename).toLowerCase();
    }

    /**
     * Определяет тип файла на основе его расширения
     *
     * @param extension расширение файла
     * @return тип файла
     * @throws FileOperationException если тип файла не поддерживается
     */
    private FileType determineFileTypeByExtension(String extension) throws FileOperationException {
        if (EXCEL_EXTENSIONS.contains(extension)) {
            return FileType.XLSX; // По умолчанию предполагаем новый формат Excel
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
        String extension = extractFileExtension(fileName);

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
            String detectedCharset = detectEncodingFromBytes(bytes);

            if (detectedCharset != null) {
                return getCharsetOrDefault(detectedCharset);
            }
        } catch (Exception e) {
            log.error("Ошибка при определении кодировки: {}", e.getMessage());
        }

        return StandardCharsets.UTF_8; // По умолчанию используем UTF-8
    }

    /**
     * Получает объект Charset по имени кодировки или возвращает UTF-8 по умолчанию
     *
     * @param encodingName имя кодировки
     * @return объект Charset
     */
    private Charset getCharsetOrDefault(String encodingName) {
        try {
            return Charset.forName(encodingName);
        } catch (Exception e) {
            log.warn("Обнаруженная кодировка '{}' не поддерживается, используем UTF-8", encodingName);
            return StandardCharsets.UTF_8;
        }
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
            String detectedCharset = detectEncodingFromBytes(bytes);

            if (detectedCharset != null) {
                return getCharsetOrDefault(detectedCharset);
            }
        } catch (Exception e) {
            log.error("Ошибка при определении кодировки: {}", e.getMessage());
        }

        return StandardCharsets.UTF_8;
    }

    /**
     * Обнаруживает кодировку из массива байтов
     *
     * @param bytes массив байтов
     * @return имя обнаруженной кодировки или null
     */
    private String detectEncodingFromBytes(byte[] bytes) {
        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(bytes, 0, Math.min(bytes.length, CHARSET_DETECTION_BUFFER_SIZE));
        detector.dataEnd();

        String encoding = detector.getDetectedCharset();
        detector.reset();

        return encoding;
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
            return determineExcelTypeFromStream(is);
        } catch (Exception e) {
            log.error("Ошибка при определении типа Excel-файла: {}", e.getMessage());
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
            return determineExcelTypeFromStream(is);
        } catch (Exception e) {
            log.error("Ошибка при определении типа Excel-файла: {}", e.getMessage());
            throw new FileOperationException("Ошибка при определении типа Excel-файла: " + e.getMessage());
        }
    }

    /**
     * Определяет тип Excel-файла из потока
     *
     * @param is поток данных
     * @return тип файла (XLS или XLSX)
     * @throws IOException если возникла ошибка чтения
     */
    private FileType determineExcelTypeFromStream(InputStream is) throws IOException {
        if (FileMagic.valueOf(is) == FileMagic.OLE2) {
            return FileType.XLS;
        } else {
            return FileType.XLSX;
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
        return createWorkbookFromStream(is, fileType);
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
        return createWorkbookFromStream(is, fileType);
    }

    /**
     * Создает объект Workbook из потока данных
     *
     * @param is поток данных
     * @param fileType тип файла
     * @return объект Workbook
     * @throws IOException если произошла ошибка при создании Workbook
     */
    private Workbook createWorkbookFromStream(InputStream is, FileType fileType) throws IOException {
        switch (fileType) {
            case XLS:
                return new HSSFWorkbook(new POIFSFileSystem(is));
            case XLSX:
                return new XSSFWorkbook(is);
            default:
                throw new IOException("Неподдерживаемый тип файла для создания Workbook: " + fileType);
        }
    }
}