package my.java.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import my.java.util.transformer.ValueTransformerFactory;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "av_handbook")
public class AvHandbook implements ImportableEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;


    private String handbookRetailNetworkCode;
    private String handbookRetailNetwork;
    private String handbookPhysicalAddress;
    private String handbookPriceZoneCode;
    private String handbookWebSite;
    private String handbookRegionCode;
    private String handbookRegionName;

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
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Код Розничной Сети", "handbookRetailNetworkCode");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Розничная Сеть", "handbookRetailNetwork");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Физический Адрес", "handbookPhysicalAddress");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Код Ценовой Зоны", "handbookPriceZoneCode");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Web-Сайт", "handbookWebSite");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Код Региона", "handbookRegionCode");
        UI_DISPLAY_NAMES_TO_ENTITY_FIELDS.put("Название Региона", "handbookRegionName");
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

    @Override
    public void setClientId(Long clientId) {

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
                "handbookRetailNetworkCode", "handbookRetailNetwork", "handbookPhysicalAddress", "handbookPriceZoneCode", "handbookWebSite",
                "handbookRegionCode", "handbookRegionName"
        );

        return validFieldNames.contains(fieldName);
    }

    /**
     * Устанавливает значение поля по его имени.
     *
     * @param fieldName имя поля
     * @param value     строковое значение
     * @return true, если значение успешно установлено
     */
    private boolean setFieldValue(String fieldName, String value) {
        try {
            switch (fieldName) {
                case "handbookRetailNetworkCode":
                    this.handbookRetailNetworkCode = value;
                    break;
                case "handbookRetailNetwork":
                    this.handbookRetailNetwork = value;
                    break;
                case "handbookPhysicalAddress":
                    this.handbookPhysicalAddress = value;
                    break;
                case "handbookPriceZoneCode":
                    this.handbookPriceZoneCode = value;
                    break;
                case "handbookWebSite":
                    this.handbookWebSite = value;
                    break;
                case "handbookRegionCode":
                    this.handbookRegionCode = value;
                    break;
                case "handbookRegionName":
                    this.handbookRegionName = value;
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
        if (handbookRetailNetworkCode == null || handbookRetailNetworkCode.trim().isEmpty()) {
            return "Не указано название сайта конкурента";
        }
        return null;
    }






    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AvHandbook handbook = (AvHandbook) o;
        return Objects.equals(handbookRetailNetworkCode, handbook.handbookRetailNetworkCode)
                && Objects.equals(handbookRetailNetwork, handbook.handbookRetailNetwork)
                && Objects.equals(handbookPhysicalAddress, handbook.handbookPhysicalAddress)
                && Objects.equals(handbookPriceZoneCode, handbook.handbookPriceZoneCode)
                && Objects.equals(handbookWebSite, handbook.handbookWebSite)
                && Objects.equals(handbookRegionCode, handbook.handbookRegionCode)
                && Objects.equals(handbookRegionName, handbook.handbookRegionName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(handbookRetailNetworkCode, handbookRetailNetwork, handbookPhysicalAddress, handbookPriceZoneCode, handbookWebSite, handbookRegionCode, handbookRegionName);
    }
}
