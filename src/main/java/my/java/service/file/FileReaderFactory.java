package my.java.service.file;

import lombok.extern.slf4j.Slf4j;
import my.java.service.file.FileTypeDetector.FileType;
import my.java.util.PathResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Фабрика для создания соответствующих FileReader в зависимости от типа файла.
 * Реализует паттерн Factory Method для выбора конкретной реализации.
 */
@Component
@Slf4j
public class FileReaderFactory {

    private final FileTypeDetector fileTypeDetector;
    private final PathResolver pathResolver;

    /**
     * Создает новую фабрику FileReader.
     *
     * @param fileTypeDetector детектор типа файла
     * @param pathResolver утилитный класс для работы с путями
     */
    @Autowired
    public FileReaderFactory(FileTypeDetector fileTypeDetector, PathResolver pathResolver) {
        this.fileTypeDetector = fileTypeDetector;
        this.pathResolver = pathResolver;
    }

    /**
     * Создает FileReader для указанного пути к файлу.
     *
     * @param filePath путь к файлу
     * @return реализация FileReader, соответствующая типу файла
     * @throws IOException если произошла ошибка при создании ридера
     */
    public FileReader createReader(Path filePath) throws IOException {
        // Определяем тип файла
        FileType fileType = fileTypeDetector.detectFileType(filePath);
        log.debug("Создание reader для файла типа: {} по пути: {}", fileType, filePath);

        // Создаем соответствующий reader
        return createReaderByType(filePath, fileType);
    }

    /**
     * Создает FileReader соответствующего типа.
     *
     * @param filePath путь к файлу
     * @param fileType тип файла
     * @return реализация FileReader
     * @throws IOException если произошла ошибка при создании ридера
     */
    private FileReader createReaderByType(Path filePath, FileType fileType) throws IOException {
        switch (fileType) {
            case CSV:
                return createCsvReader(filePath);
            case XLS:
            case XLSX:
                return createExcelReader(filePath, fileType);
            default:
                throw new IOException("Неподдерживаемый тип файла: " + fileType);
        }
    }

    /**
     * Создает CSV-ридер для указанного пути.
     *
     * @param filePath путь к файлу
     * @return CsvFileReader
     * @throws IOException если произошла ошибка при создании ридера
     */
    private FileReader createCsvReader(Path filePath) throws IOException {
        CsvFileReader reader = new CsvFileReader(fileTypeDetector, pathResolver);
        reader.initializeFromPath(filePath);
        return reader;
    }

    /**
     * Создает Excel-ридер для указанного пути.
     *
     * @param filePath путь к файлу
     * @param fileType тип Excel-файла
     * @return ExcelFileReader
     * @throws IOException если произошла ошибка при создании ридера
     */
    private FileReader createExcelReader(Path filePath, FileType fileType) throws IOException {
        ExcelFileReader reader = new ExcelFileReader(fileTypeDetector, pathResolver);
        reader.initializeFromPath(filePath, fileType);
        return reader;
    }

    /**
     * Адаптер для файловых ридеров с интерфейсом FileReader.
     * Базовый класс с общей функциональностью для адаптеров ридеров.
     */
    private abstract static class FileReaderAdapter implements FileReader {

        protected final Path filePath;

        /**
         * Создает новый адаптер файлового ридера.
         *
         * @param filePath путь к файлу
         */
        protected FileReaderAdapter(Path filePath) {
            this.filePath = filePath;
        }

        @Override
        public void initialize(org.springframework.web.multipart.MultipartFile file) throws IOException {
            throw new UnsupportedOperationException("Этот адаптер уже инициализирован из пути к файлу");
        }
    }
}