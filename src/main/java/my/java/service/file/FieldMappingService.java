package my.java.service.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.service.file.transformer.ValueTransformerFactory;
import my.java.util.FieldDescriptionUtils;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Сервис для сопоставления полей и применения трансформаторов
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FieldMappingService {

    private final ValueTransformerFactory transformerFactory;

    /**
     * Создает сопоставление между заголовками файла и полями сущности
     *
     * @param entityClass класс сущности
     * @param headers заголовки файла
     * @return map, где ключ - заголовок файла, значение - поле сущности
     */
    public Map<String, Field> createFieldMapping(Class<?> entityClass, List<String> headers) {
        // Получаем сопоставление полей сущности с их описаниями из аннотаций
        Map<String, String> fieldDescriptions = FieldDescriptionUtils.getFieldDescriptions(entityClass);

        // Получаем сопоставление описаний полей и их имен
        Map<String, String> descriptionToField = FieldDescriptionUtils.getDescriptionToFieldMap(entityClass);

        // Создаем результирующее сопоставление
        Map<String, Field> result = new HashMap<>();

        // Получаем все поля класса
        Map<String, Field> allFields = new HashMap<>();
        for (Field field : FieldDescriptionUtils.getAllFields(entityClass)) {
            allFields.put(field.getName(), field);
        }

        // Сопоставляем заголовки с полями
        for (String header : headers) {
            String trimmedHeader = header.trim();

            // Проверяем точное соответствие с описанием поля
            if (descriptionToField.containsKey(trimmedHeader)) {
                String fieldName = descriptionToField.get(trimmedHeader);
                if (allFields.containsKey(fieldName)) {
                    result.put(trimmedHeader, allFields.get(fieldName));
                    continue;
                }
            }

            // Проверяем без учета регистра
            for (Map.Entry<String, String> entry : descriptionToField.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(trimmedHeader)) {
                    String fieldName = entry.getValue();
                    if (allFields.containsKey(fieldName)) {
                        result.put(trimmedHeader, allFields.get(fieldName));
                        break;
                    }
                }
            }
        }

        return result;
    }

    /**
     * Создает новый экземпляр сущности и заполняет его данными из строки файла
     *
     * @param entityClass класс сущности
     * @param rowData данные строки файла
     * @param fieldMapping сопоставление полей
     * @return созданный и заполненный экземпляр сущности
     */
    public <T> T createEntity(Class<T> entityClass, Map<String, String> rowData, Map<String, Field> fieldMapping) {
        try {
            // Создаем новый экземпляр сущности
            T entity = entityClass.getDeclaredConstructor().newInstance();

            // Заполняем поля данными
            for (Map.Entry<String, Field> entry : fieldMapping.entrySet()) {
                String header = entry.getKey();
                Field field = entry.getValue();

                // Пропускаем поля, помеченные как skipMapping
                if (FieldDescriptionUtils.shouldSkipField(field)) {
                    continue;
                }

                // Получаем значение из данных строки
                String stringValue = rowData.get(header);

                // Если значение не найдено, пропускаем поле
                if (stringValue == null) {
                    continue;
                }

                // Пробуем установить значение
                setFieldValue(entity, field, stringValue);
            }

            return entity;
        } catch (Exception e) {
            log.error("Error creating entity: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Устанавливает значение поля сущности
     *
     * @param entity экземпляр сущности
     * @param field поле
     * @param stringValue строковое значение
     */
    private void setFieldValue(Object entity, Field field, String stringValue) {
        try {
            field.setAccessible(true);

            // Получаем тип поля
            Class<?> fieldType = field.getType();

            // Преобразуем строковое значение в нужный тип
            Object typedValue = transformerFactory.transform(stringValue, fieldType, null);

            // Устанавливаем значение поля
            if (typedValue != null) {
                field.set(entity, typedValue);
            }
        } catch (Exception e) {
            log.warn("Could not set field value: {} = '{}', error: {}", field.getName(), stringValue, e.getMessage());
        }
    }

    /**
     * Проверяет, соответствуют ли все обязательные поля сущности заголовкам файла
     *
     * @param entityClass класс сущности
     * @param headers заголовки файла
     * @return список отсутствующих обязательных полей или пустой список, если все обязательные поля присутствуют
     */
    public List<String> validateRequiredFields(Class<?> entityClass, List<String> headers) {
        // Получаем сопоставление полей сущности с их описаниями из аннотаций
        Map<String, String> fieldDescriptions = FieldDescriptionUtils.getFieldDescriptions(entityClass);

        // Получаем сопоставление описаний полей и их имен
        Map<String, String> descriptionToField = FieldDescriptionUtils.getDescriptionToFieldMap(entityClass);

        // Получаем все обязательные поля
        Set<String> requiredFields = new HashSet<>();
        for (Field field : FieldDescriptionUtils.getAllFields(entityClass)) {
            // Пропускаем поля, помеченные как skipMapping
            if (FieldDescriptionUtils.shouldSkipField(field)) {
                continue;
            }

            // Проверяем обязательность поля (может быть определена по-разному)
            // TODO: Добавить проверку обязательности поля по аннотациям JPA или других фреймворков
            String fieldName = field.getName();
            if (fieldDescriptions.containsKey(fieldName)) {
                requiredFields.add(fieldName);
            }
        }

        // Создаем сопоставление заголовков с полями
        Map<String, Field> fieldMapping = createFieldMapping(entityClass, headers);

        // Проверяем, что все обязательные поля сопоставлены
        List<String> missingFields = new ArrayList<>();
        for (String requiredField : requiredFields) {
            boolean found = false;
            for (Field mappedField : fieldMapping.values()) {
                if (mappedField.getName().equals(requiredField)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                // Добавляем описание поля в список отсутствующих
                missingFields.add(fieldDescriptions.getOrDefault(requiredField, requiredField));
            }
        }

        return missingFields;
    }
}