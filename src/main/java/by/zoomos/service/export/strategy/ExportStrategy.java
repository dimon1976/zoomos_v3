package by.zoomos.service.export.strategy;

import by.zoomos.model.entity.Product;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;

/**
 * Интерфейс для стратегий экспорта данных
 */
public interface ExportStrategy {

    /**
     * Экспортирует данные в файл
     *
     * @param products список продуктов для экспорта
     * @param params дополнительные параметры экспорта
     * @return ресурс с экспортированным файлом
     */
    Resource export(List<Product> products, Map<String, String> params);

    /**
     * Возвращает имя файла для экспорта
     *
     * @return имя файла с расширением
     */
    String getFileName();

    /**
     * Возвращает MIME-тип экспортируемого файла
     *
     * @return MIME-тип
     */
    String getContentType();

    /**
     * Проверяет, поддерживает ли стратегия данный формат
     *
     * @param format формат экспорта
     * @return true если формат поддерживается
     */
    boolean supports(String format);
}