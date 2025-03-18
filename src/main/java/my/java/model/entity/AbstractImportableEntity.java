package my.java.model.entity;

import jakarta.persistence.Transient;
import my.java.service.file.transformer.ValueTransformerFactory;

import java.util.Map;

/**
 * Абстрактный базовый класс для сущностей, которые могут быть импортированы из файлов.
 * Содержит общую логику для всех импортируемых сущностей.
 */
public abstract class AbstractImportableEntity implements ImportableEntity {

    @Transient
    protected ValueTransformerFactory transformerFactory;

    /**
     * Устанавливает фабрику трансформеров для преобразования строковых значений.
     *
     * @param transformerFactory фабрика трансформеров
     */
    public void setTransformerFactory(ValueTransformerFactory transformerFactory) {
        this.transformerFactory = transformerFactory;
    }

    /**
     * Заполняет поля сущности из карты с данными.
     *
     * @param data карта, где ключ - имя поля (или заголовок файла), значение - строковое значение
     * @return true, если заполнение прошло успешно
     */
    @Override
    public boolean fillFromMap(Map<String, String> data) {
        if (transformerFactory == null) {
            throw new IllegalStateException("TransformerFactory не установлен");
        }

        boolean success = true;
        Map<String, String> mappings = getFieldMappings();

        for (Map.Entry<String, String> entry : data.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Пропускаем пустые значения
            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            // Проверка прямого совпадения с именем поля
            if (mappings.values().contains(key)) {
                success &= setFieldValue(key, value);
                continue;
            }

            // Поиск в маппинге
            String fieldName = mappings.get(key);
            if (fieldName == null) {
                // Поиск без учета регистра
                for (Map.Entry<String, String> mapping : mappings.entrySet()) {
                    if (mapping.getKey().equalsIgnoreCase(key)) {
                        fieldName = mapping.getValue();
                        break;
                    }
                }
            }

            if (fieldName != null) {
                success &= setFieldValue(fieldName, value);
            }
        }

        return success;
    }

    /**
     * Устанавливает значение поля по его имени.
     * Должен быть реализован в конкретных классах-наследниках.
     *
     * @param fieldName имя поля
     * @param value строковое значение
     * @return true, если значение успешно установлено
     */
    protected abstract boolean setFieldValue(String fieldName, String value);
}