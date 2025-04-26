package my.java.service.export.strategy;

import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.model.export.ExportTemplate;

import java.nio.file.Path;
import java.util.Map;

/**
 * Интерфейс стратегии экспорта данных
 */
public interface ExportStrategy {

    /**
     * Возвращает уникальный идентификатор стратегии
     */
    String getStrategyId();

    /**
     * Проверяет, подходит ли стратегия для заданных параметров
     */
    boolean isApplicable(String entityType, Map<String, String> parameters);

    /**
     * Выполняет экспорт данных
     *
     * @param client клиент, для которого выполняется экспорт
     * @param template шаблон экспорта с параметрами и маппингом полей
     * @param operation операция экспорта для обновления статуса
     * @return путь к созданному файлу
     */
    Path executeExport(Client client, ExportTemplate template, FileOperation operation);

    /**
     * Возвращает список поддерживаемых форматов
     */
    String[] getSupportedFormats();
}