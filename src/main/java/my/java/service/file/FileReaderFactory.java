package my.java.service.file;

import lombok.extern.slf4j.Slf4j;
import my.java.service.file.FileTypeDetector.FileType;
import my.java.util.PathResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Фабрика для создания FileReader из Path
 */
@Component
@Slf4j
public class FileReaderFactory {

    private final FileTypeDetector fileTypeDetector;
    private final PathResolver pathResolver;

    @Autowired
    public FileReaderFactory(FileTypeDetector fileTypeDetector, PathResolver pathResolver) {
        this.fileTypeDetector = fileTypeDetector;
        this.pathResolver = pathResolver;
    }

    /**
     * Создает FileReader для указанного пути к файлу
     *
     * @param filePath путь к файлу
     * @return реализация FileReader
     * @throws IOException если произошла ошибка при чтении
     */
    public FileReader createReader(Path filePath) throws IOException {
        // Определяем тип файла
        FileType fileType = fileTypeDetector.detectFileType(filePath);

        // Создаем соответствующий reader
        switch (fileType) {
            case CSV:
                return new CsvFileReaderAdapter(filePath);
            case XLS:
            case XLSX:
                return new ExcelFileReaderAdapter(filePath, fileType);
            default:
                throw new IOException("Неподдерживаемый тип файла: " + fileType);
        }
    }

    /**
     * Адаптер для CsvFileReader с поддержкой Path
     */
    private class CsvFileReaderAdapter implements FileReader {

        private final CsvFileReader delegate;

        /**
         * Создает адаптер для CsvFileReader
         *
         * @param filePath путь к файлу
         * @throws IOException если произошла ошибка при чтении
         */
        public CsvFileReaderAdapter(Path filePath) throws IOException {
            log.debug("Creating CsvFileReaderAdapter for path: {}", filePath);
            this.delegate = new CsvFileReader(fileTypeDetector, pathResolver);
            delegate.initializeFromPath(filePath);
        }

        @Override
        public void initialize(org.springframework.web.multipart.MultipartFile file) throws IOException {
            throw new UnsupportedOperationException("This adapter is already initialized from a path");
        }

        @Override
        public java.util.List<String> getHeaders() throws IOException {
            return delegate.getHeaders();
        }

        @Override
        public java.util.Map<String, String> readNextRow() throws IOException {
            return delegate.readNextRow();
        }

        @Override
        public java.util.List<java.util.Map<String, String>> readChunk(int chunkSize) throws IOException {
            return delegate.readChunk(chunkSize);
        }

        @Override
        public java.util.List<java.util.Map<String, String>> readAll() throws IOException {
            return delegate.readAll();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public long estimateRowCount() {
            return delegate.estimateRowCount();
        }

        @Override
        public long getCurrentPosition() {
            return delegate.getCurrentPosition();
        }

        @Override
        public boolean hasMoreRows() {
            return delegate.hasMoreRows();
        }
    }

    /**
     * Адаптер для ExcelFileReader с поддержкой Path
     */
    private class ExcelFileReaderAdapter implements FileReader {

        private final ExcelFileReader delegate;

        /**
         * Создает адаптер для ExcelFileReader
         *
         * @param filePath путь к файлу
         * @param fileType тип Excel-файла
         * @throws IOException если произошла ошибка при чтении
         */
        public ExcelFileReaderAdapter(Path filePath, FileType fileType) throws IOException {
            log.debug("Creating ExcelFileReaderAdapter for path: {}", filePath);
            this.delegate = new ExcelFileReader(fileTypeDetector, pathResolver);
            delegate.initializeFromPath(filePath, fileType);
        }

        @Override
        public void initialize(org.springframework.web.multipart.MultipartFile file) throws IOException {
            throw new UnsupportedOperationException("This adapter is already initialized from a path");
        }

        @Override
        public java.util.List<String> getHeaders() throws IOException {
            return delegate.getHeaders();
        }

        @Override
        public java.util.Map<String, String> readNextRow() throws IOException {
            return delegate.readNextRow();
        }

        @Override
        public java.util.List<java.util.Map<String, String>> readChunk(int chunkSize) throws IOException {
            return delegate.readChunk(chunkSize);
        }

        @Override
        public java.util.List<java.util.Map<String, String>> readAll() throws IOException {
            return delegate.readAll();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public long estimateRowCount() {
            return delegate.estimateRowCount();
        }

        @Override
        public long getCurrentPosition() {
            return delegate.getCurrentPosition();
        }

        @Override
        public boolean hasMoreRows() {
            return delegate.hasMoreRows();
        }
    }
}