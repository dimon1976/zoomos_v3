package my.java.service.file;

import my.java.exception.FileOperationException;
import org.springframework.web.multipart.MultipartFile;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Интерфейс для чтения различных типов файлов
 */
public interface FileReader extends Closeable {

    /**
     * Инициализирует чтение файла
     *
     * @param file файл для чтения
     * @throws IOException если произошла ошибка при чтении
     */
    void initialize(MultipartFile file) throws IOException;

    /**
     * Возвращает заголовки файла
     *
     * @return список заголовков
     * @throws IOException если произошла ошибка при чтении
     */
    List<String> getHeaders() throws IOException;

    /**
     * Читает следующую строку из файла
     *
     * @return Map, где ключи - заголовки, значения - данные
     * @throws IOException если произошла ошибка при чтении
     */
    Map<String, String> readNextRow() throws IOException;

    /**
     * Читает порцию строк (чанк) указанного размера
     *
     * @param chunkSize размер чанка
     * @return список строк данных
     * @throws IOException если произошла ошибка при чтении
     */
    List<Map<String, String>> readChunk(int chunkSize) throws IOException;

    /**
     * Читает все строки из файла
     *
     * @return список всех строк данных
     * @throws IOException если произошла ошибка при чтении
     */
    List<Map<String, String>> readAll() throws IOException;

    /**
     * Закрывает ресурсы, использованные при чтении файла
     *
     * @throws IOException если произошла ошибка при закрытии
     */
    @Override
    void close() throws IOException;

    /**
     * Возвращает примерное количество строк в файле (без заголовка)
     *
     * @return количество строк данных
     */
    long estimateRowCount();

    /**
     * Возвращает текущую позицию чтения (номер строки)
     *
     * @return номер текущей строки
     */
    long getCurrentPosition();

    /**
     * Проверка, есть ли еще строки для чтения
     *
     * @return true, если есть еще строки
     */
    boolean hasMoreRows();

    /**
     * Создает экземпляр FileReader для указанного типа файла
     *
     * @param file файл для чтения
     * @param fileType тип файла
     * @return экземпляр FileReader
     * @throws FileOperationException если тип файла не поддерживается
     */
    static FileReader createReader(MultipartFile file, FileTypeDetector.FileType fileType) throws FileOperationException {
        try {
            switch (fileType) {
                case CSV:
                    FileReader csvReader = new CsvFileReader();
                    csvReader.initialize(file);
                    return csvReader;
                case XLS:
                case XLSX:
                    FileReader excelReader = new ExcelFileReader();
                    excelReader.initialize(file);
                    return excelReader;
                default:
                    throw new FileOperationException("Неподдерживаемый тип файла: " + fileType);
            }
        } catch (IOException e) {
            throw new FileOperationException("Ошибка при создании reader-а для файла: " + e.getMessage(), e);
        }
    }
}