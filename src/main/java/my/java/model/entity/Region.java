package my.java.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import my.java.util.transformer.ValueTransformerFactory;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.ZonedDateTime;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    private Long clientId;
    private String region;

    @Column(length = 400)
    private String regionAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // =====================================================================================
    // ВНИМАНИЕ! ЭТО НЕ ЗАГОЛОВКИ CSV ФАЙЛОВ!
    //
    // Это человекочитаемые названия для отображения в интерфейсе при создании маппингов.
    // Пользователь видит эти названия и сопоставляет их с реальными заголовками CSV.
    //
    // Реальные заголовки CSV приходят через FieldMappingDetail.sourceField!
    // =====================================================================================
    private static final Map<String, String> UI_DISPLAY_NAMES_TO_ENTITY_FIELDS = new HashMap<>();

    static {
        // "Как показать пользователю в UI" -> "имя поля в Java сущности"
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Город", "region");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Адрес", "regionAddress");
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
        if (transformerFactory == null) {
            throw new IllegalStateException("TransformerFactory не установлен");
        }

        boolean success = true;

        for (Map.Entry<String, String> entry : data.entrySet()) {
            String fieldName = entry.getKey();  // это уже имя поля Java-сущности
            String value = entry.getValue();

            // Пропускаем пустые значения
            if (value == null || value.trim().isEmpty()) {
                continue;
            }

            // Проверяем, что это валидное имя поля
            if (!isValidFieldName(fieldName)) {
                continue;
            }

            // Устанавливаем значение поля
            success &= setFieldValue(fieldName, value);
        }

        return success;
    }

    private boolean isValidFieldName(String fieldName) {
        // Определяем набор валидных имен полей для данной сущности
        Set<String> validFieldNames = Set.of(
                "region", "regionAddress"
        );

        return validFieldNames.contains(fieldName);
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
        return new HashMap<>(UI_DISPLAY_NAMES_TO_ENTITY_FIELDS);
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