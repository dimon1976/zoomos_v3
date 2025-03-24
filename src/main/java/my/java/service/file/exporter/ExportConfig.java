package my.java.service.file.exporter;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Конфигурация экспорта данных
 * Путь: /java/my/java/service/file/exporter/ExportConfig.java
 */
@Data
@Builder
public class ExportConfig {
    /**
     * Соответствия между полями сущности и заголовками в файле
     */
    @Builder.Default
    private Map<String, String> fieldMappings = new HashMap<>();

    /**
     * Поля, которые нужно включить в экспорт (пустой список = все поля)
     */
    @Builder.Default
    private List<String> includedFields = new ArrayList<>();

    /**
     * Поля, которые нужно исключить из экспорта
     */
    @Builder.Default
    private List<String> excludedFields = new ArrayList<>();

    /**
     * Настройки форматирования для определенных полей
     */
    @Builder.Default
    private Map<String, String> formatSettings = new HashMap<>();

    /**
     * Включать ли заголовок в файл
     */
    @Builder.Default
    private boolean includeHeader = true;

    /**
     * Размер пакета для обработки
     */
    @Builder.Default
    private int batchSize = 1000;

    /**
     * Использовать асинхронную обработку
     */
    @Builder.Default
    private boolean asyncProcessing = false;

    /**
     * Применять форматирование к данным
     */
    @Builder.Default
    private boolean applyFormatting = true;

    /**
     * Дополнительные настройки экспорта
     */
    @Builder.Default
    private Map<String, Object> additionalOptions = new HashMap<>();

    /**
     * Создает конфигурацию экспорта по умолчанию
     * @return конфигурация с настройками по умолчанию
     */
    public static ExportConfig createDefault() {
        return ExportConfig.builder()
                .includeHeader(true)
                .applyFormatting(true)
                .batchSize(1000)
                .asyncProcessing(false)
                .build();
    }

    /**
     * Добавляет соответствие поля и заголовка
     * @param fieldName имя поля сущности
     * @param headerName имя заголовка в файле
     * @return текущая конфигурация
     */
    public ExportConfig addFieldMapping(String fieldName, String headerName) {
        this.fieldMappings.put(fieldName, headerName);
        return this;
    }

    /**
     * Добавляет поле для включения в экспорт
     * @param fieldName имя поля
     * @return текущая конфигурация
     */
    public ExportConfig includeField(String fieldName) {
        this.includedFields.add(fieldName);
        return this;
    }

    /**
     * Добавляет поле для исключения из экспорта
     * @param fieldName имя поля
     * @return текущая конфигурация
     */
    public ExportConfig excludeField(String fieldName) {
        this.excludedFields.add(fieldName);
        return this;
    }

    /**
     * Добавляет настройку форматирования для поля
     * @param fieldName имя поля
     * @param formatPattern шаблон форматирования
     * @return текущая конфигурация
     */
    public ExportConfig addFormatSetting(String fieldName, String formatPattern) {
        this.formatSettings.put(fieldName, formatPattern);
        return this;
    }
}