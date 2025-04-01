package my.java.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import my.java.service.file.transformer.ValueTransformerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Сущность, представляющая региональные данные.
 */
@Setter
@Getter
@Entity
@Table(name = "region_data")
public class Region implements ImportableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long clientId;
    private String region;

    @Column(length = 400)
    private String regionAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // Статическая карта сопоставления заголовков файла с полями сущности
    private static final Map<String, String> FIELD_MAPPINGS = new HashMap<>();

    static {
        // Инициализация маппинга заголовков и полей
        FIELD_MAPPINGS.put("Город", "region");
        FIELD_MAPPINGS.put("Адрес", "regionAddress");
    }

    // Транзитивные поля, не сохраняемые в БД
    @Transient
    private ValueTransformerFactory transformerFactory;

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
        boolean success = true;

        for (Map.Entry<String, String> entry : data.entrySet()) {
            String header = entry.getKey();
            String value = entry.getValue();

            // Пропускаем пустые значения
            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            // Получаем имя поля из маппинга
            String fieldName = FIELD_MAPPINGS.get(header);
            if (fieldName == null) {
                // Пробуем без учета регистра
                for (Map.Entry<String, String> mapping : FIELD_MAPPINGS.entrySet()) {
                    if (mapping.getKey().equalsIgnoreCase(header)) {
                        fieldName = mapping.getValue();
                        break;
                    }
                }

                // Если все еще не нашли, пропускаем
                if (fieldName == null) {
                    continue;
                }
            }

            // Устанавливаем значение поля
            success &= setFieldValue(fieldName, value);
        }

        return success;
    }

    /**
     * Устанавливает значение поля по его имени.
     *
     * @param fieldName имя поля
     * @param value строковое значение
     * @return true, если значение успешно установлено
     */
    private boolean setFieldValue(String fieldName, String value) {
        try {
            switch (fieldName) {
                case "region":
                    this.region = value;
                    break;
                case "regionAddress":
                    this.regionAddress = value;
                    break;
                default:
                    return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Возвращает карту соответствия заголовков файла и полей сущности.
     *
     * @return карта, где ключ - заголовок файла, значение - имя поля в сущности
     */
    @Override
    public Map<String, String> getFieldMappings() {
        return new HashMap<>(FIELD_MAPPINGS);
    }

    /**
     * Валидирует заполненную сущность.
     *
     * @return null, если валидация прошла успешно, иначе - сообщение об ошибке
     */
    @Override
    public String validate() {
        if (region == null || region.trim().isEmpty()) {
            return "Не указан город";
        }
        return null;
    }
}