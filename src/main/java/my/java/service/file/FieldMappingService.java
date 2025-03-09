package my.java.service.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.service.file.transformer.ValueTransformerFactory;
import my.java.util.FieldDescriptionUtils;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Сервис для сопоставления полей и применения трансформаторов.
 * Обеспечивает преобразование данных из файлов в объекты сущностей.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FieldMappingService {

    private final ValueTransformerFactory transformerFactory;

    /**
     * Создает сопоставление между заголовками файла и полями сущности.
     *
     * @param entityClass класс сущности
     * @param headers заголовки файла
     * @return map, где ключ - заголовок файла, значение - поле сущности
     */
    public Map<String, Field> createFieldMapping(Class<?> entityClass, List<String> headers) {
        Map<String, String> fieldDescriptions = FieldDescriptionUtils.getFieldDescriptions(entityClass);
        Map<String, String> descriptionToField = FieldDescriptionUtils.getDescriptionToFieldMap(entityClass);
        Map<String, Field> allFields = getAllFieldsMap(entityClass);

        return mapHeadersToFields(headers, descriptionToField, allFields);
    }

    /**
     * Создает Map всех полей класса.
     *
     * @param entityClass класс сущности
     * @return Map, где ключ - имя поля, значение - поле
     */
    private Map<String, Field> getAllFieldsMap(Class<?> entityClass) {
        Map<String, Field> allFields = new HashMap<>();
        for (Field field : FieldDescriptionUtils.getAllFields(entityClass)) {
            field.setAccessible(true);
            allFields.put(field.getName(), field);
        }
        return allFields;
    }

    /**
     * Сопоставляет заголовки файла с полями сущности.
     *
     * @param headers заголовки файла
     * @param descriptionToField сопоставление описаний полей и их имен
     * @param allFields Map всех полей класса
     * @return Map сопоставлений заголовков и полей
     */
    private Map<String, Field> mapHeadersToFields(List<String> headers,
                                                  Map<String, String> descriptionToField,
                                                  Map<String, Field> allFields) {
        Map<String, Field> result = new HashMap<>();

        for (String header : headers) {
            String trimmedHeader = header.trim();

            // Проверяем точное соответствие с описанием поля
            if (descriptionToField.containsKey(trimmedHeader)) {
                addFieldToMapping(result, trimmedHeader, descriptionToField.get(trimmedHeader), allFields);
                continue;
            }

            // Проверяем без учета регистра
            findFieldIgnoringCase(result, trimmedHeader, descriptionToField, allFields);
        }

        return result;
    }

    /**
     * Добавляет поле в сопоставление, если оно существует.
     *
     * @param mapping сопоставление заголовков и полей
     * @param header заголовок
     * @param fieldName имя поля
     * @param allFields Map всех полей класса
     */
    private void addFieldToMapping(Map<String, Field> mapping, String header,
                                   String fieldName, Map<String, Field> allFields) {
        if (allFields.containsKey(fieldName)) {
            mapping.put(header, allFields.get(fieldName));
        }
    }

    /**
     * Находит поле, игнорируя регистр.
     *
     * @param mapping сопоставление заголовков и полей
     * @param header заголовок
     * @param descriptionToField сопоставление описаний полей и их имен
     * @param allFields Map всех полей класса
     */
    private void findFieldIgnoringCase(Map<String, Field> mapping, String header,
                                       Map<String, String> descriptionToField,
                                       Map<String, Field> allFields) {
        for (Map.Entry<String, String> entry : descriptionToField.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(header)) {
                addFieldToMapping(mapping, header, entry.getValue(), allFields);
                break;
            }
        }
    }

    /**
     * Создает новый экземпляр сущности и заполняет его данными из строки файла.
     *
     * @param entityClass класс сущности
     * @param rowData данные строки файла
     * @param fieldMapping сопоставление полей
     * @return созданный и заполненный экземпляр сущности
     */
    public <T> T createEntity(Class<T> entityClass, Map<String, String> rowData, Map<String, Field> fieldMapping) {
        try {
            T entity = instantiateEntity(entityClass);
            populateEntityFields(entity, rowData, fieldMapping);
            return entity;
        } catch (Exception e) {
            log.error("Ошибка создания сущности: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Создает экземпляр сущности.
     *
     * @param entityClass класс сущности
     * @return новый экземпляр сущности
     * @throws Exception если не удалось создать экземпляр
     */
    private <T> T instantiateEntity(Class<T> entityClass) throws Exception {
        return entityClass.getDeclaredConstructor().newInstance();
    }

    /**
     * Заполняет поля сущности данными из строки файла.
     *
     * @param entity экземпляр сущности
     * @param rowData данные строки файла
     * @param fieldMapping сопоставление полей
     */
    private void populateEntityFields(Object entity, Map<String, String> rowData,
                                      Map<String, Field> fieldMapping) {
        for (Map.Entry<String, Field> entry : fieldMapping.entrySet()) {
            String header = entry.getKey();
            Field field = entry.getValue();

            if (shouldSkipField(field)) {
                continue;
            }

            String stringValue = rowData.get(header);
            if (stringValue != null) {
                setFieldValue(entity, field, stringValue);
            }
        }
    }

    /**
     * Проверяет, нужно ли пропустить поле при заполнении.
     *
     * @param field поле для проверки
     * @return true, если поле нужно пропустить
     */
    private boolean shouldSkipField(Field field) {
        return FieldDescriptionUtils.shouldSkipField(field);
    }

    /**
     * Устанавливает значение поля сущности.
     *
     * @param entity экземпляр сущности
     * @param field поле
     * @param stringValue строковое значение
     */
    private void setFieldValue(Object entity, Field field, String stringValue) {
        try {
            field.setAccessible(true);
            Class<?> fieldType = field.getType();
            Object typedValue = transformerFactory.transform(stringValue, fieldType, null);

            if (typedValue != null) {
                field.set(entity, typedValue);
            }
        } catch (Exception e) {
            log.warn("Не удалось установить значение поля: {} = '{}', ошибка: {}",
                    field.getName(), stringValue, e.getMessage());
        }
    }

    /**
     * Проверяет, соответствуют ли все обязательные поля сущности заголовкам файла.
     *
     * @param entityClass класс сущности
     * @param headers заголовки файла
     * @return список отсутствующих обязательных полей или пустой список
     */
    public List<String> validateRequiredFields(Class<?> entityClass, List<String> headers) {
        Map<String, String> fieldDescriptions = FieldDescriptionUtils.getFieldDescriptions(entityClass);
        Set<String> requiredFields = findRequiredFields(entityClass, fieldDescriptions);

        // Создаем сопоставление заголовков с полями
        Map<String, Field> fieldMapping = createFieldMapping(entityClass, headers);

        return findMissingRequiredFields(requiredFields, fieldMapping, fieldDescriptions);
    }

    /**
     * Находит обязательные поля сущности.
     *
     * @param entityClass класс сущности
     * @param fieldDescriptions описания полей
     * @return множество имен обязательных полей
     */
    private Set<String> findRequiredFields(Class<?> entityClass, Map<String, String> fieldDescriptions) {
        Set<String> requiredFields = new HashSet<>();

        for (Field field : FieldDescriptionUtils.getAllFields(entityClass)) {
            if (shouldSkipField(field)) {
                continue;
            }

            // Проверяем обязательность поля
            // TODO: Добавить проверку обязательности поля по аннотациям JPA или других фреймворков
            String fieldName = field.getName();
            if (fieldDescriptions.containsKey(fieldName)) {
                requiredFields.add(fieldName);
            }
        }

        return requiredFields;
    }

    /**
     * Находит отсутствующие обязательные поля.
     *
     * @param requiredFields множество имен обязательных полей
     * @param fieldMapping сопоставление заголовков и полей
     * @param fieldDescriptions описания полей
     * @return список описаний отсутствующих обязательных полей
     */
    private List<String> findMissingRequiredFields(Set<String> requiredFields,
                                                   Map<String, Field> fieldMapping,
                                                   Map<String, String> fieldDescriptions) {
        List<String> missingFields = new ArrayList<>();

        for (String requiredField : requiredFields) {
            boolean found = isFieldMapped(requiredField, fieldMapping);

            if (!found) {
                // Добавляем описание поля в список отсутствующих
                missingFields.add(fieldDescriptions.getOrDefault(requiredField, requiredField));
            }
        }

        return missingFields;
    }

    /**
     * Проверяет, сопоставлено ли поле с заголовком.
     *
     * @param fieldName имя поля
     * @param fieldMapping сопоставление заголовков и полей
     * @return true, если поле сопоставлено
     */
    private boolean isFieldMapped(String fieldName, Map<String, Field> fieldMapping) {
        for (Field mappedField : fieldMapping.values()) {
            if (mappedField.getName().equals(fieldName)) {
                return true;
            }
        }
        return false;
    }
}