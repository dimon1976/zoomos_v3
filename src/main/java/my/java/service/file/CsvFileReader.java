package my.java.service.file;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
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

    private CSVReader csvReader;
    private List<String> headers;
    private String[] nextLine;
    private Path tempFilePath;
    private long rowCount;
    private long currentPosition;
    private FileTypeDetector fileTypeDetector;
    private PathResolver pathResolver;

    /**
     * Конструктор по умолчанию
     */
    public CsvFileReader() {
    }

    /**
     * Конструктор с указанием зависимостей
     *
     * @param fileTypeDetector детектор типа файла
     * @param pathResolver утилита для работы с путями
     */
    @Autowired
    public CsvFileReader(FileTypeDetector fileTypeDetector, PathResolver pathResolver) {
        this.fileTypeDetector = fileTypeDetector;
        this.pathResolver = pathResolver;
    }

    /**
     * Конструктор для чтения CSV-файла из Path
     *
     * @param filePath путь к файлу
     * @param fileTypeDetector детектор типа файла
     * @param pathResolver утилита для работы с путями
     * @throws IOException если произошла ошибка при чтении
     */
    public CsvFileReader(Path filePath, FileTypeDetector fileTypeDetector, PathResolver pathResolver) throws IOException {
        this.fileTypeDetector = fileTypeDetector;
        this.pathResolver = pathResolver;
        initializeFromPath(filePath);
    }

    /**
     * Инициализирует чтение CSV-файла
     *
     * @param file CSV-файл для чтения
     * @throws IOException если произошла ошибка при чтении
     */
    @Override
    public void initialize(MultipartFile file) throws IOException {
        if (fileTypeDetector == null || pathResolver == null) {
            throw new IllegalStateException("FileTypeDetector and PathResolver must be set before initialization");
        }

        // Определяем кодировку файла
        Charset charset = fileTypeDetector.detectCharset(file);
        log.debug("Detected charset for CSV file: {}", charset.displayName());

        // Сохраняем файл временно для возможности нескольких проходов
        tempFilePath = pathResolver.saveToTempFile(file, "csv_");

        initializeReader(tempFilePath, charset);
    }

    /**
     * Инициализирует чтение CSV-файла из Path
     *
     * @param filePath путь к файлу
     * @throws IOException если произошла ошибка при чтении
     */
    public void initializeFromPath(Path filePath) throws IOException {
        if (fileTypeDetector == null) {
            throw new IllegalStateException("FileTypeDetector must be set before initialization");
        }

        // Определяем кодировку файла
        Charset charset = fileTypeDetector.detectCharset(filePath);
        log.debug("Detected charset for CSV file: {}", charset.displayName());

        // Сохраняем путь к файлу
        tempFilePath = filePath;

        initializeReader(filePath, charset);
    }

    /**
     * Инициализирует CSV Reader
     *
     * @param filePath путь к файлу
     * @param charset кодировка файла
     * @throws IOException если произошла ошибка при чтении
     */
    private void initializeReader(Path filePath, Charset charset) throws IOException {
        // Настраиваем CSV-парсер
        com.opencsv.CSVParser csvParser = new CSVParserBuilder()
                .withSeparator(detectSeparator(filePath, charset))
                .withQuoteChar('"')
                .withIgnoreQuotations(false)
                .build();

        // Создаем CSV Reader
        csvReader = new CSVReaderBuilder(new InputStreamReader(new FileInputStream(filePath.toFile()), charset))
                .withCSVParser(csvParser)
                .build();

        // Читаем заголовки
        try {
            String[] headerRow = csvReader.readNext();
            if (headerRow != null) {
                // Обрабатываем заголовки: удаляем BOM-символ из первого заголовка, если он есть
                if (headerRow.length > 0 && headerRow[0].startsWith("\uFEFF")) {
                    headerRow[0] = headerRow[0].substring(1);
                }

                // Обрезаем пробелы в заголовках
                for (int i = 0; i < headerRow.length; i++) {
                    headerRow[i] = headerRow[i].trim();
                }

                this.headers = Arrays.asList(headerRow);
            } else {
                this.headers = Collections.emptyList();
                throw new IOException("CSV-файл не содержит строк");
            }

            // Вычисляем примерное количество строк
            this.rowCount = countLines(filePath, charset) - 1; // Вычитаем строку заголовка
            log.debug("Estimated row count: {}", rowCount);

        } catch (CsvValidationException e) {
            throw new IOException("Ошибка при чтении заголовков CSV: " + e.getMessage(), e);
        }

        // Сбрасываем текущую позицию
        this.currentPosition = 0;
    }

    /**
     * Возвращает заголовки CSV-файла
     *
     * @return список заголовков
     */
    @Override
    public List<String> getHeaders() {
        return headers;
    }

    /**
     * Читает следующую строку из CSV-файла
     *
     * @return Map, где ключи - заголовки, значения - данные
     * @throws IOException если произошла ошибка при чтении
     */
    @Override
    public Map<String, String> readNextRow() throws IOException {
        if (csvReader == null) {
            throw new IllegalStateException("Файл не инициализирован. Вызовите метод initialize() перед чтением.");
        }

        try {
            String[] row = nextLine != null ? nextLine : csvReader.readNext();
            nextLine = null;

            if (row == null) {
                return null; // Достигнут конец файла
            }

            currentPosition++;

            Map<String, String> rowData = new LinkedHashMap<>();
            for (int i = 0; i < Math.min(headers.size(), row.length); i++) {
                rowData.put(headers.get(i), row[i] != null ? row[i].trim() : "");
            }

            // Если колонок в строке меньше, чем заголовков, заполняем пустыми значениями
            if (row.length < headers.size()) {
                for (int i = row.length; i < headers.size(); i++) {
                    rowData.put(headers.get(i), "");
                }
            }

            return rowData;
        } catch (CsvValidationException e) {
            throw new IOException("Ошибка при чтении строки CSV: " + e.getMessage(), e);
        }
    }

    /**
     * Читает порцию строк (чанк) указанного размера
     *
     * @param chunkSize размер чанка
     * @return список строк данных
     * @throws IOException если произошла ошибка при чтении
     */
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

    /**
     * Читает все строки из CSV-файла
     *
     * @return список всех строк данных
     * @throws IOException если произошла ошибка при чтении
     */
    @Override
    public List<Map<String, String>> readAll() throws IOException {
        List<Map<String, String>> allRows = new ArrayList<>();

        Map<String, String> row;
        while ((row = readNextRow()) != null) {
            allRows.add(row);
        }

        return allRows;
    }

    /**
     * Закрывает ресурсы, использованные при чтении CSV-файла
     *
     * @throws IOException если произошла ошибка при закрытии
     */
    @Override
    public void close() throws IOException {
        if (csvReader != null) {
            csvReader.close();
        }
    }

    /**
     * Возвращает примерное количество строк в CSV-файле (без заголовка)
     *
     * @return количество строк данных
     */
    @Override
    public long estimateRowCount() {
        return rowCount;
    }

    /**
     * Возвращает текущую позицию чтения (номер строки)
     *
     * @return номер текущей строки
     */
    @Override
    public long getCurrentPosition() {
        return currentPosition;
    }

    /**
     * Проверка, есть ли еще строки для чтения
     *
     * @return true, если есть еще строки
     */
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

    /**
     * Определяет разделитель для CSV-файла на основе его содержимого
     *
     * @param filePath путь к CSV-файлу
     * @param charset кодировка файла
     * @return символ-разделитель
     * @throws IOException если произошла ошибка при чтении
     */
    private char detectSeparator(Path filePath, Charset charset) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(filePath, charset)) {
            // Читаем первую строку
            String firstLine = reader.readLine();
            if (firstLine == null) {
                return ','; // Если файл пуст, используем запятую по умолчанию
            }

            // Удаляем BOM-символ, если он есть
            if (firstLine.startsWith("\uFEFF")) {
                firstLine = firstLine.substring(1);
            }

            // Подсчитываем количество разных разделителей
            int commaCount = countChar(firstLine, ',');
            int semicolonCount = countChar(firstLine, ';');
            int tabCount = countChar(firstLine, '\t');

            // Выбираем разделитель с наибольшим количеством
            if (semicolonCount > commaCount && semicolonCount > tabCount) {
                return ';';
            } else if (tabCount > commaCount && tabCount > semicolonCount) {
                return '\t';
            } else {
                return ','; // Запятая по умолчанию
            }
        }
    }

    /**
     * Подсчитывает количество вхождений символа в строке
     *
     * @param str строка для анализа
     * @param ch символ для подсчета
     * @return количество вхождений
     */
    private int countChar(String str, char ch) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == ch) {
                count++;
            }
        }
        return count;
    }

    /**
     * Подсчитывает количество строк в файле
     *
     * @param filePath путь к файлу
     * @param charset кодировка файла
     * @return количество строк
     * @throws IOException если произошла ошибка при чтении
     */
    private long countLines(Path filePath, Charset charset) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(filePath, charset)) {
            long count = 0;
            while (reader.readLine() != null) {
                count++;
            }
            return count;
        }
    }
}