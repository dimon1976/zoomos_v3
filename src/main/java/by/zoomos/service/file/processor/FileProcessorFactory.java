package by.zoomos.service.file.processor;

import by.zoomos.exception.FileProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Фабрика для создания процессоров файлов
 */
@Component
@RequiredArgsConstructor
public class FileProcessorFactory {

    private final List<FileProcessor> fileProcessors;

    /**
     * Возвращает подходящий процессор для обработки файла
     *
     * @param file файл для обработки
     * @return процессор файла
     * @throws FileProcessingException если подходящий процессор не найден
     */
    public FileProcessor getProcessor(MultipartFile file) {
        String fileName = file.getOriginalFilename();

        return fileProcessors.stream()
                .filter(processor -> processor.supports(fileName))
                .findFirst()
                .orElseThrow(() -> new FileProcessingException(
                        "Неподдерживаемый формат файла", fileName));
    }

    /**
     * Проверяет, поддерживается ли формат файла
     *
     * @param fileName имя файла
     * @return true если формат поддерживается
     */
    public boolean isSupported(String fileName) {
        return fileProcessors.stream()
                .anyMatch(processor -> processor.supports(fileName));
    }
}
