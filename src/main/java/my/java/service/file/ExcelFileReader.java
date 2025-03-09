package my.java.service.file;

import lombok.extern.slf4j.Slf4j;
import my.java.service.file.FileTypeDetector.FileType;
import my.java.util.PathResolver;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Реализация FileReader для чтения Excel-файлов
 */
@Slf4j
@Service
public class ExcelFileReader implements FileReader {

    private Workbook workbook;
    private List<String> headers;
    private Sheet activeSheet;
    private int totalRows;
    private int currentRowIndex;
    private FileTypeDetector fileTypeDetector;
    private PathResolver pathResolver;

    /**
     * Конструктор по умолчанию
     */
    public ExcelFileReader() {
    }

    /**
     * Конструктор с указанием зависимостей
     *
     * @param fileTypeDetector детектор типа файла
     * @param pathResolver утилита для работы с путями
     */
    @Autowired
    public ExcelFileReader(FileTypeDetector fileTypeDetector, PathResolver pathResolver) {
        this.fileTypeDetector = fileTypeDetector;
        this.pathResolver = pathResolver;
    }

    /**
     * Конструктор для чтения Excel-файла из Path
     *
     * @param filePath путь к файлу
     * @param fileType тип Excel-файла
     * @param fileTypeDetector детектор типа файла
     * @param pathResolver утилита для работы с путями
     * @throws IOException если произошла ошибка при чтении
     */
    public ExcelFileReader(Path filePath, FileType fileType, FileTypeDetector fileTypeDetector, PathResolver pathResolver) throws IOException {
        this.fileTypeDetector = fileTypeDetector;
        this.pathResolver = pathResolver;
        initializeFromPath(filePath, fileType);
    }

    /**
     * Инициализирует чтение Excel-файла
     *
     * @param file Excel-файл для чтения
     * @throws IOException если произошла ошибка при чтении
     */
    @Override
    public void initialize(MultipartFile file) throws IOException {
        if (fileTypeDetector == null) {
            throw new IllegalStateException("FileTypeDetector must be set before initialization");
        }

        // Определяем тип Excel-файла
        FileType fileType = fileTypeDetector.detectFileType(file);

        // Открываем книгу Excel
        workbook = fileTypeDetector.createWorkbook(file, fileType);

        initializeWorkbook();
    }

    /**
     * Инициализирует чтение Excel-файла из Path
     *
     * @param filePath путь к файлу
     * @param fileType тип Excel-файла
     * @throws IOException если произошла ошибка при чтении
     */
    public void initializeFromPath(Path filePath, FileType fileType) throws IOException {
        if (fileTypeDetector == null) {
            throw new IllegalStateException("FileTypeDetector must be set before initialization");
        }

        // Открываем книгу Excel
        workbook = fileTypeDetector.createWorkbook(filePath, fileType);

        initializeWorkbook();
    }

    /**
     * Инициализирует книгу Excel
     *
     * @throws IOException если произошла ошибка при инициализации
     */
    private void initializeWorkbook() throws IOException {
        // Выбираем активный лист (первый по умолчанию)
        if (workbook.getNumberOfSheets() == 0) {
            throw new IOException("Excel-файл не содержит листов");
        }

        activeSheet = workbook.getSheetAt(0);

        // Читаем заголовки из первой строки
        Row headerRow = activeSheet.getRow(0);
        if (headerRow == null) {
            throw new IOException("Отсутствует строка заголовков в Excel-файле");
        }

        // Определяем количество колонок
        int lastCellNum = headerRow.getLastCellNum();
        headers = new ArrayList<>(lastCellNum);

        // Читаем заголовки
        for (int i = 0; i < lastCellNum; i++) {
            Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            String headerValue = getCellValueAsString(cell).trim();
            headers.add(headerValue);
        }

        // Определяем количество строк в файле
        totalRows = activeSheet.getLastRowNum();

        // Устанавливаем текущую позицию - после заголовка
        currentRowIndex = 1;

        log.debug("Excel file initialized. Sheets: {}, Rows: {}, Headers: {}",
                workbook.getNumberOfSheets(), totalRows, headers);
    }

    /**
     * Возвращает заголовки Excel-файла
     *
     * @return список заголовков
     */
    @Override
    public List<String> getHeaders() {
        return headers;
    }

    /**
     * Читает следующую строку из Excel-файла
     *
     * @return Map, где ключи - заголовки, значения - данные
     * @throws IOException если произошла ошибка при чтении
     */
    @Override
    public Map<String, String> readNextRow() throws IOException {
        if (currentRowIndex > totalRows) {
            return null; // Конец файла
        }

        Row row = activeSheet.getRow(currentRowIndex++);
        if (row == null) {
            // Пропускаем пустые строки
            return readNextRow();
        }

        Map<String, String> rowData = new LinkedHashMap<>();

        // Проверяем, пуста ли вся строка
        boolean hasData = false;
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                hasData = true;
                break;
            }
        }

        if (!hasData) {
            // Если строка пустая, пропускаем ее
            return readNextRow();
        }

        // Читаем данные строки
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            String value = getCellValueAsString(cell);
            rowData.put(headers.get(i), value);
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
     * Читает все строки из Excel-файла
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
     * Закрывает ресурсы, использованные при чтении Excel-файла
     *
     * @throws IOException если произошла ошибка при закрытии
     */
    @Override
    public void close() throws IOException {
        if (workbook != null) {
            workbook.close();
        }
    }

    /**
     * Возвращает примерное количество строк в Excel-файле (без заголовка)
     *
     * @return количество строк данных
     */
    @Override
    public long estimateRowCount() {
        return totalRows;
    }

    /**
     * Возвращает текущую позицию чтения (номер строки)
     *
     * @return номер текущей строки
     */
    @Override
    public long getCurrentPosition() {
        return currentRowIndex - 1;
    }

    /**
     * Проверка, есть ли еще строки для чтения
     *
     * @return true, если есть еще строки
     */
    @Override
    public boolean hasMoreRows() {
        return currentRowIndex <= totalRows;
    }

    /**
     * Возвращает значение ячейки Excel как строку
     *
     * @param cell ячейка Excel
     * @return строковое представление значения ячейки
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Форматируем дату
                    DataFormatter formatter = new DataFormatter();
                    return formatter.formatCellValue(cell);
                } else {
                    // Избегаем научной нотации для чисел
                    double value = cell.getNumericCellValue();
                    if (value == Math.floor(value)) {
                        return String.format("%.0f", value);
                    } else {
                        return String.valueOf(value);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    // Пытаемся получить строковый результат формулы
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        // Пытаемся получить числовой результат формулы
                        double value = cell.getNumericCellValue();
                        if (value == Math.floor(value)) {
                            return String.format("%.0f", value);
                        } else {
                            return String.valueOf(value);
                        }
                    } catch (Exception ex) {
                        // Возвращаем текст формулы, если не удалось получить результат
                        return cell.getCellFormula();
                    }
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }
}