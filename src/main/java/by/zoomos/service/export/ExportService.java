package by.zoomos.service.export;

import by.zoomos.exception.ExportException;
import by.zoomos.model.entity.Product;
import by.zoomos.service.ProductService;
import by.zoomos.service.export.strategy.ExportStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис для экспорта данных
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final ProductService productService;
    private final List<ExportStrategy> exportStrategies;

    /**
     * Экспортирует данные в выбранный формат
     *
     * @param clientId идентификатор клиента
     * @param format формат экспорта
     * @param params дополнительные параметры
     * @return ресурс с экспортированным файлом
     * @throws ExportException если произошла ошибка при экспорте
     */
    @Transactional(readOnly = true)
    public Resource exportData(Long clientId, String format, Map<String, String> params) {
        log.info("Начало экспорта данных для клиента: {}, формат: {}", clientId, format);

        try {
            // Находим подходящую стратегию экспорта
            ExportStrategy strategy = exportStrategies.stream()
                    .filter(s -> s.supports(format))
                    .findFirst()
                    .orElseThrow(() -> new ExportException("Неподдерживаемый формат экспорта: " + format));

            // Получаем данные для экспорта
            List<Product> products = productService.streamProductsByClientId(clientId)
                    .collect(Collectors.toList());

            log.info("Получено {} продуктов для экспорта", products.size());

            // Выполняем экспорт
            return strategy.export(products, params);

        } catch (Exception e) {
            String errorMsg = "Ошибка при экспорте данных";
            log.error(errorMsg, e);
            throw new ExportException(errorMsg, e);
        }
    }

    /**
     * Возвращает имя файла для экспорта
     *
     * @param format формат экспорта
     * @return имя файла
     * @throws ExportException если формат не поддерживается
     */
    public String getFileName(String format) {
        return exportStrategies.stream()
                .filter(s -> s.supports(format))
                .findFirst()
                .map(ExportStrategy::getFileName)
                .orElseThrow(() -> new ExportException("Неподдерживаемый формат экспорта: " + format));
    }

    /**
     * Возвращает MIME-тип для экспорта
     *
     * @param format формат экспорта
     * @return MIME-тип
     * @throws ExportException если формат не поддерживается
     */
    public String getContentType(String format) {
        return exportStrategies.stream()
                .filter(s -> s.supports(format))
                .findFirst()
                .map(ExportStrategy::getContentType)
                .orElseThrow(() -> new ExportException("Неподдерживаемый формат экспорта: " + format));
    }
}