package my.java.service.file;

import lombok.extern.slf4j.Slf4j;
import my.java.service.file.FileTypeDetector.FileType;
import my.java.util.PathResolver;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

/**
 * Реализация FileReader для чтения Excel-файлов
 */
@Slf4j
@Service
public class ExcelFileReader implements FileReader {

    private static final int INITIAL_LIST_CAPACITY = 100;
    private static final int FIRST_SHEET_INDEX = 0;
    private static final int HEADER_ROW_INDEX = 0;

    private Workbook workbook;
    private List<String> headers;
    private Sheet activeSheet;
    private int totalRows;
    private int currentRowIndex;
    private FileTypeDetector fileTypeDetector;
    private PathResolver pathResolver;

    public ExcelFileReader() {
    }

    @Autowired
    public ExcelFileReader(FileTypeDetector fileTypeDetector, PathResolver pathResolver) {
        this.fileTypeDetector = fileTypeDetector;
        this.pathResolver = pathResolver;
    }

    public ExcelFileReader(Path filePath, FileType fileType, FileTypeDetector fileTypeDetector, PathResolver pathResolver)
            throws IOException {
        this.fileTypeDetector = fileTypeDetector;
        this.pathResolver = pathResolver;
        initializeFromPath(filePath, fileType);
    }

    @Override
    public void initialize(MultipartFile file) throws IOException {
        validateDependencies();

        FileType fileType = fileTypeDetector.detectFileType(file);
        workbook = fileTypeDetector.createWorkbook(file, fileType);

        initializeWorkbook();
    }

    public void initializeFromPath(Path filePath, FileType fileType) throws IOException {
        validateDependencies();

        workbook = fileTypeDetector.createWorkbook(filePath, fileType);

        initializeWorkbook();
    }

    @Override
    public List<String> getHeaders() {
        return headers;
    }

    @Override
    public Map<String, String> readNextRow() throws IOException {
        if (isEndOfFile()) {
            return null;
        }

        Row row = activeSheet.getRow(currentRowIndex++);
        if (row == null) {
            return readNextRow(); // Пропускаем пустые строки
        }

        if (isEmptyRow(row)) {
            return readNextRow(); // Пропускаем строки без данных
        }

        return convertRowToMap(row);
    }

    @Override
    public List<Map<String, String>> readChunk(int chunkSize) throws IOException {
        List<Map<String, String>> chunk = new ArrayList<>(chunkSize);

        for (int i = 0; i < chunkSize && hasMoreRows(); i++) {
            Map<String, String> row = readNextRow();
            if (row == null) {
                break;
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
        if (workbook != null) {
            workbook.close();
        }
    }

    @Override
    public long estimateRowCount() {
        return totalRows;
    }

    @Override
    public long getCurrentPosition() {
        return currentRowIndex - 1;
    }

    @Override
    public boolean hasMoreRows() {
        return currentRowIndex <= totalRows;
    }

    // Вспомогательные методы

    private void validateDependencies() {
        if (fileTypeDetector == null) {
            throw new IllegalStateException("FileTypeDetector must be set before initialization");
        }
    }

    private void initializeWorkbook() throws IOException {
        validateWorkbookHasSheets();

        activeSheet = workbook.getSheetAt(FIRST_SHEET_INDEX);

        headers = readHeadersFromFirstRow();
        totalRows = activeSheet.getLastRowNum();
        currentRowIndex = HEADER_ROW_INDEX + 1; // Начинаем с первой строки данных

        logInitializationDetails();
    }

    private void validateWorkbookHasSheets() throws IOException {
        if (workbook.getNumberOfSheets() == 0) {
            throw new IOException("Excel-файл не содержит листов");
        }
    }

    private List<String> readHeadersFromFirstRow() throws IOException {
        Row headerRow = activeSheet.getRow(HEADER_ROW_INDEX);

        if (headerRow == null) {
            throw new IOException("Отсутствует строка заголовков в Excel-файле");
        }

        int columnCount = headerRow.getLastCellNum();
        List<String> headerList = new ArrayList<>(columnCount);

        for (int i = 0; i < columnCount; i++) {
            Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            String headerValue = getCellValueAsString(cell).trim();
            headerList.add(headerValue);
        }

        return headerList;
    }

    private void logInitializationDetails() {
        log.debug("Excel file initialized. Sheets: {}, Rows: {}, Headers: {}",
                workbook.getNumberOfSheets(), totalRows, headers);
    }

    private boolean isEndOfFile() {
        return currentRowIndex > totalRows;
    }

    private boolean isEmptyRow(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private Map<String, String> convertRowToMap(Row row) {
        Map<String, String> rowData = new LinkedHashMap<>(headers.size());

        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            String value = getCellValueAsString(cell);
            rowData.put(headers.get(i), value);
        }

        return rowData;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return formatNumericCell(cell);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return getFormulaResult(cell);
            case BLANK:
            default:
                return "";
        }
    }

    private String formatNumericCell(Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            return formatDateCell(cell);
        } else {
            return formatNumberCell(cell);
        }
    }

    private String formatDateCell(Cell cell) {
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell);
    }

    private String formatNumberCell(Cell cell) {
        double value = cell.getNumericCellValue();
        // Если число целое, не показываем десятичную часть
        if (value == Math.floor(value)) {
            return String.format("%.0f", value);
        } else {
            return String.valueOf(value);
        }
    }

    private String getFormulaResult(Cell cell) {
        try {
            return cell.getStringCellValue();
        } catch (Exception e) {
            try {
                return formatNumberCell(cell);
            } catch (Exception ex) {
                return cell.getCellFormula();
            }
        }
    }
}