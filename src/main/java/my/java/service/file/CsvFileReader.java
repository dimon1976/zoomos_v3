package my.java.service.file;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import my.java.util.PathResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Реализация FileReader для чтения CSV-файлов
 */
@Slf4j
@Service
public class CsvFileReader implements FileReader {

    private static final String BOM_MARKER = "\uFEFF";
    private static final char DEFAULT_SEPARATOR = ',';
    private static final char[] POSSIBLE_SEPARATORS = {',', ';', '\t'};

    private CSVReader csvReader;
    private List<String> headers;
    private String[] nextLine;
    private Path filePath;
    private long rowCount;
    private long currentPosition;
    private FileTypeDetector fileTypeDetector;
    private PathResolver pathResolver;

    public CsvFileReader() {
    }

    @Autowired
    public CsvFileReader(FileTypeDetector fileTypeDetector, PathResolver pathResolver) {
        this.fileTypeDetector = fileTypeDetector;
        this.pathResolver = pathResolver;
    }

    public CsvFileReader(Path filePath, FileTypeDetector fileTypeDetector, PathResolver pathResolver) throws IOException {
        this.fileTypeDetector = fileTypeDetector;
        this.pathResolver = pathResolver;
        initializeFromPath(filePath);
    }

    @Override
    public void initialize(MultipartFile file) throws IOException {
        validateDependencies();

        Charset charset = fileTypeDetector.detectCharset(file);
        log.debug("Detected charset for CSV file: {}", charset.displayName());

        Path tempFilePath = pathResolver.saveToTempFile(file, "csv_");
        this.filePath = tempFilePath;

        initializeReader(tempFilePath, charset);
    }

    public void initializeFromPath(Path filePath) throws IOException {
        validateFileTypeDetector();

        Charset charset = fileTypeDetector.detectCharset(filePath);
        log.debug("Detected charset for CSV file: {}", charset.displayName());

        this.filePath = filePath;

        initializeReader(filePath, charset);
    }

    private void initializeReader(Path filePath, Charset charset) throws IOException {
        char separator = detectSeparator(filePath, charset);
        com.opencsv.CSVParser csvParser = createCsvParser(separator);

        csvReader = new CSVReaderBuilder(
                new InputStreamReader(new FileInputStream(filePath.toFile()), charset))
                .withCSVParser(csvParser)
                .build();

        readHeaders();
        countRows(filePath, charset);
        resetPosition();
    }

    @Override
    public List<String> getHeaders() {
        return headers;
    }

    @Override
    public Map<String, String> readNextRow() throws IOException {
        validateReaderInitialized();

        try {
            String[] row = getNextRow();

            if (row == null) {
                return null; // Достигнут конец файла
            }

            incrementPosition();
            return createRowMap(row);

        } catch (CsvValidationException e) {
            throw new IOException("Ошибка при чтении строки CSV: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, String>> readChunk(int chunkSize) throws IOException {
        List<Map<String, String>> chunk = new ArrayList<>(chunkSize);

        for (int i = 0; i < chunkSize; i++) {
            Map<String, String> row = readNextRow();
            if (row == null) {
                break; // Достигнут конец файла
            }
            chunk.add(row);
        }

        return chunk;
    }

    @Override
    public List<Map<String, String>> readAll() throws IOException {
        List<Map<String, String>> allRows = new ArrayList<>();

        Map<String, String> row;
        while ((row = readNextRow()) != null) {
            allRows.add(row);
        }

        return allRows;
    }

    @Override
    public void close() throws IOException {
        if (csvReader != null) {
            csvReader.close();
        }
    }

    @Override
    public long estimateRowCount() {
        return rowCount;
    }

    @Override
    public long getCurrentPosition() {
        return currentPosition;
    }

    @Override
    public boolean hasMoreRows() {
        try {
            if (nextLine == null) {
                nextLine = csvReader.readNext();
            }
            return nextLine != null;
        } catch (Exception e) {
            log.error("Ошибка при проверке наличия строк: {}", e.getMessage());
            return false;
        }
    }

    // Вспомогательные методы

    private void validateDependencies() {
        if (fileTypeDetector == null || pathResolver == null) {
            throw new IllegalStateException(
                    "FileTypeDetector and PathResolver must be set before initialization");
        }
    }

    private void validateFileTypeDetector() {
        if (fileTypeDetector == null) {
            throw new IllegalStateException("FileTypeDetector must be set before initialization");
        }
    }

    private void validateReaderInitialized() {
        if (csvReader == null) {
            throw new IllegalStateException(
                    "Файл не инициализирован. Вызовите метод initialize() перед чтением.");
        }
    }

    private com.opencsv.CSVParser createCsvParser(char separator) {
        return new CSVParserBuilder()
                .withSeparator(separator)
                .withQuoteChar('"')
                .withIgnoreQuotations(false)
                .build();
    }

    private void readHeaders() throws IOException {
        try {
            String[] headerRow = csvReader.readNext();

            if (headerRow == null) {
                this.headers = Collections.emptyList();
                throw new IOException("CSV-файл не содержит строк");
            }

            headerRow = cleanupHeaders(headerRow);
            this.headers = Arrays.asList(headerRow);

        } catch (CsvValidationException e) {
            throw new IOException("Ошибка при чтении заголовков CSV: " + e.getMessage(), e);
        }
    }

    private String[] cleanupHeaders(String[] headerRow) {
        // Удаляем BOM-символ из первого заголовка
        if (headerRow.length > 0 && headerRow[0].startsWith(BOM_MARKER)) {
            headerRow[0] = headerRow[0].substring(1);
        }

        // Обрезаем пробелы в заголовках
        for (int i = 0; i < headerRow.length; i++) {
            headerRow[i] = headerRow[i].trim();
        }

        return headerRow;
    }

    private void countRows(Path filePath, Charset charset) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(filePath, charset)) {
            rowCount = 0;
            while (reader.readLine() != null) {
                rowCount++;
            }
            rowCount--; // Вычитаем строку заголовка
        }
        log.debug("Estimated row count: {}", rowCount);
    }

    private void resetPosition() {
        this.currentPosition = 0;
    }

    private String[] getNextRow() throws IOException, CsvValidationException {
        String[] row = nextLine != null ? nextLine : csvReader.readNext();
        nextLine = null;
        return row;
    }

    private void incrementPosition() {
        currentPosition++;
    }

    private Map<String, String> createRowMap(String[] row) {
        Map<String, String> rowData = new LinkedHashMap<>();

        // Добавляем данные для доступных колонок
        int columnCount = Math.min(headers.size(), row.length);
        for (int i = 0; i < columnCount; i++) {
            rowData.put(headers.get(i), cleanupCellValue(row[i]));
        }

        // Заполняем отсутствующие колонки пустыми значениями
        for (int i = row.length; i < headers.size(); i++) {
            rowData.put(headers.get(i), "");
        }

        return rowData;
    }

    private String cleanupCellValue(String value) {
        return value != null ? value.trim() : "";
    }

    private char detectSeparator(Path filePath, Charset charset) throws IOException {
        String firstLine = readFirstLine(filePath, charset);
        if (firstLine == null || firstLine.isEmpty()) {
            return DEFAULT_SEPARATOR;
        }

        // Удаляем BOM-символ, если он есть
        if (firstLine.startsWith(BOM_MARKER)) {
            firstLine = firstLine.substring(1);
        }

        // Находим разделитель с наибольшим количеством вхождений
        int maxCount = 0;
        char bestSeparator = DEFAULT_SEPARATOR;

        for (char separator : POSSIBLE_SEPARATORS) {
            int count = countChar(firstLine, separator);
            if (count > maxCount) {
                maxCount = count;
                bestSeparator = separator;
            }
        }

        return bestSeparator;
    }

    private String readFirstLine(Path filePath, Charset charset) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(filePath, charset)) {
            return reader.readLine();
        }
    }

    private int countChar(String str, char ch) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == ch) {
                count++;
            }
        }
        return count;
    }
}