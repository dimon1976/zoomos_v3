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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Реализация FileReader для чтения CSV-файлов
 */
@Slf4j
@Service
public class CsvFileReader implements FileReader {

    private static final int DEFAULT_CHUNK_SIZE = 100;
    private static final char DEFAULT_SEPARATOR = ',';
    private static final char DEFAULT_QUOTE_CHAR = '"';

    private CSVReader csvReader;
    private List<String> headers;
    private String[] nextLine;
    private Path tempFilePath;
    private long totalRowCount;
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
     * Инициализирует чтение CSV-файла
     *
     * @param file CSV-файл для чтения
     * @throws IOException если произошла ошибка при чтении
     */
    @Override
    public void initialize(MultipartFile file) throws IOException {
        validateDependenciesPresent();

        // Определяем кодировку файла
        Charset charset = fileTypeDetector.detectCharset(file);
        log.debug("Определена кодировка для CSV файла: {}", charset.displayName());

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
        validateDependenciesPresent();

        // Определяем кодировку файла
        Charset charset = fileTypeDetector.detectCharset(filePath);
        log.debug("Определена кодировка для CSV файла: {}", charset.displayName());

        // Сохраняем путь к файлу
        tempFilePath = filePath;

        initializeReader(filePath, charset);
    }

    /**
     * Проверяет, что необходимые зависимости присутствуют
     *
     * @throws IllegalStateException если зависимости отсутствуют
     */
    private void validateDependenciesPresent() {
        if (fileTypeDetector == null || pathResolver == null) {
            throw new IllegalStateException(
                    "FileTypeDetector и PathResolver должны быть установлены перед инициализацией");
        }
    }

    /**
     * Инициализирует CSV Reader
     *
     * @param filePath путь к файлу
     * @param charset кодировка файла
     * @throws IOException если произошла ошибка при чтении
     */
    private void initializeReader(Path filePath, Charset charset) throws IOException {
        char separatorChar = detectSeparator(filePath, charset);

        // Настраиваем CSV-парсер
        com.opencsv.CSVParser csvParser = createCsvParser(separatorChar);
        csvReader = createCsvReader(filePath, charset, csvParser);

        readHeadersFromCsv();
        calculateTotalRows(filePath, charset);

        // Сбрасываем текущую позицию
        currentPosition = 0;
    }

    /**
     * Создает парсер CSV с указанным разделителем
     *
     * @param separator символ-разделитель
     * @return настроенный парсер CSV
     */
    private com.opencsv.CSVParser createCsvParser(char separator) {
        return new CSVParserBuilder()
                .withSeparator(separator)
                .withQuoteChar(DEFAULT_QUOTE_CHAR)
                .withIgnoreQuotations(false)
                .build();
    }

    /**
     * Создает CSV ридер для указанного файла
     *
     * @param filePath путь к файлу
     * @param charset кодировка файла
     * @param csvParser парсер CSV
     * @return настроенный CSV ридер
     * @throws IOException если произошла ошибка при создании ридера
     */
    private CSVReader createCsvReader(Path filePath, Charset charset, com.opencsv.CSVParser csvParser)
            throws IOException {
        InputStreamReader reader = new InputStreamReader(new FileInputStream(filePath.toFile()), charset);
        return new CSVReaderBuilder(reader)
                .withCSVParser(csvParser)
                .build();
    }

    /**
     * Читает заголовки из CSV-файла
     *
     * @throws IOException если произошла ошибка при чтении
     */
    private void readHeadersFromCsv() throws IOException {
        try {
            String[] headerRow = csvReader.readNext();
            if (headerRow != null) {
                headers = processHeaderRow(headerRow);
            } else {
                headers = Collections.emptyList();
                throw new IOException("CSV-файл не содержит строк");
            }
        } catch (CsvValidationException e) {
            throw new IOException("Ошибка при чтении заголовков CSV: " + e.getMessage(), e);
        }
    }

    /**
     * Обрабатывает строку заголовков
     *
     * @param headerRow массив заголовков
     * @return список обработанных заголовков
     */
    private List<String> processHeaderRow(String[] headerRow) {
        // Обрабатываем заголовки: удаляем BOM-символ из первого заголовка, если он есть
        if (headerRow.length > 0 && headerRow[0].startsWith("\uFEFF")) {
            headerRow[0] = headerRow[0].substring(1);
        }

        // Обрезаем пробелы в заголовках
        for (int i = 0; i < headerRow.length; i++) {
            headerRow[i] = headerRow[i].trim();
        }

        return Arrays.asList(headerRow);
    }

    /**
     * Вычисляет общее количество строк в файле
     *
     * @param filePath путь к файлу
     * @param charset кодировка файла
     * @throws IOException если произошла ошибка при чтении
     */
    private void calculateTotalRows(Path filePath, Charset charset) throws IOException {
        // Вычисляем примерное количество строк
        totalRowCount = countLines(filePath, charset) - 1; // Вычитаем строку заголовка
        log.debug("Оценочное количество строк: {}", totalRowCount);
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
        validateReaderInitialized();

        try {
            String[] row = getNextDataRow();

            if (row == null) {
                return null; // Достигнут конец файла
            }

            currentPosition++;
            return convertRowToMap(row);

        } catch (CsvValidationException e) {
            throw new IOException("Ошибка при чтении строки CSV: " + e.getMessage(), e);
        }
    }

    /**
     * Проверяет, что ридер инициализирован
     *
     * @throws IllegalStateException если ридер не инициализирован
     */
    private void validateReaderInitialized() {
        if (csvReader == null) {
            throw new IllegalStateException("Файл не инициализирован. Вызовите метод initialize() перед чтением.");
        }
    }

    /**
     * Получает следующую строку данных
     *
     * @return массив значений строки или null, если достигнут конец файла
     * @throws IOException если произошла ошибка при чтении
     * @throws CsvValidationException если произошла ошибка валидации CSV
     */
    private String[] getNextDataRow() throws IOException, CsvValidationException {
        String[] row = nextLine != null ? nextLine : csvReader.readNext();
        nextLine = null;
        return row;
    }

    /**
     * Преобразует массив значений строки в Map
     *
     * @param row массив значений строки
     * @return Map, где ключи - заголовки, значения - данные
     */
    private Map<String, String> convertRowToMap(String[] row) {
        Map<String, String> rowData = new LinkedHashMap<>();

        // Заполняем данные по доступным колонкам
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
        return totalRowCount;
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
                return DEFAULT_SEPARATOR; // Если файл пуст, используем запятую по умолчанию
            }

            // Удаляем BOM-символ, если он есть
            firstLine = removeBomIfPresent(firstLine);

            // Подсчитываем количество разных разделителей
            int commaCount = countChar(firstLine, ',');
            int semicolonCount = countChar(firstLine, ';');
            int tabCount = countChar(firstLine, '\t');

            // Выбираем разделитель с наибольшим количеством
            return determineSeparatorByCount(commaCount, semicolonCount, tabCount);
        }
    }

    /**
     * Удаляет BOM-символ из строки, если он присутствует
     *
     * @param line строка для обработки
     * @return строка без BOM-символа
     */
    private String removeBomIfPresent(String line) {
        if (line.startsWith("\uFEFF")) {
            return line.substring(1);
        }
        return line;
    }

    /**
     * Определяет разделитель по количеству вхождений
     *
     * @param commaCount количество запятых
     * @param semicolonCount количество точек с запятой
     * @param tabCount количество табуляций
     * @return символ-разделитель
     */
    private char determineSeparatorByCount(int commaCount, int semicolonCount, int tabCount) {
        if (semicolonCount > commaCount && semicolonCount > tabCount) {
            return ';';
        } else if (tabCount > commaCount && tabCount > semicolonCount) {
            return '\t';
        } else {
            return DEFAULT_SEPARATOR; // Запятая по умолчанию
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