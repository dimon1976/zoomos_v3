package my.java.util;

import lombok.extern.slf4j.Slf4j;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Утилитный класс для работы с аннотацией FieldDescription
 */
@Slf4j
public class FieldDescriptionUtils {

    private static final String ANNOTATION_NAME = "FieldDescription";

    /**
     * Получает сопоставление полей сущности с их описаниями из аннотаций FieldDescription
     *
     * @param entityClass класс сущности
     * @return Map, где ключ - имя поля, значение - описание из аннотации
     */
    public static Map<String, String> getFieldDescriptions(Class<?> entityClass) {
        Map<String, String> result = new HashMap<>();

        for (Field field : getAllFields(entityClass)) {
            try {
                field.setAccessible(true);

                // Поиск аннотации FieldDescription
                Annotation fieldDescAnnotation = findFieldDescriptionAnnotation(field);

                if (fieldDescAnnotation != null) {
                    // Получаем значение аннотации через рефлексию
                    String value = getAnnotationValue(fieldDescAnnotation, "value");
                    Boolean skipMapping = getAnnotationValue(fieldDescAnnotation, "skipMapping");

                    if (skipMapping == null || !skipMapping) {
                        result.put(field.getName(), value);
                    }
                }
            } catch (Exception e) {
                log.error("Error getting field description for field {}: {}", field.getName(), e.getMessage());
            }
        }

        return result;
    }

    /**
     * Находит аннотацию FieldDescription в любом пакете
     *
     * @param field поле для поиска аннотации
     * @return найденная аннотация или null
     */
    private static Annotation findFieldDescriptionAnnotation(Field field) {
        for (Annotation annotation : field.getAnnotations()) {
            if (annotation.annotationType().getSimpleName().equals(ANNOTATION_NAME)) {
                return annotation;
            }
        }
        return null;
    }

    /**
     * Получает значение атрибута аннотации через рефлексию
     *
     * @param annotation аннотация
     * @param attributeName имя атрибута
     * @param <T> тип возвращаемого значения
     * @return значение атрибута или null
     */
    @SuppressWarnings("unchecked")
    private static <T> T getAnnotationValue(Annotation annotation, String attributeName) {
        try {
            Method method = annotation.annotationType().getDeclaredMethod(attributeName);
            return (T) method.invoke(annotation);
        } catch (Exception e) {
            log.debug("Could not get annotation attribute {}: {}", attributeName, e.getMessage());
            return null;
        }
    }

    /**
     * Получает сопоставление описаний полей и их имен в сущности
     *
     * @param entityClass класс сущности
     * @return Map, где ключ - описание из аннотации, значение - имя поля
     */
    public static Map<String, String> getDescriptionToFieldMap(Class<?> entityClass) {
        return getFieldDescriptions(entityClass).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (first, second) -> first));
    }

    /**
     * Получает все поля класса, включая поля его суперклассов
     *
     * @param clazz класс
     * @return список полей
     */
    public static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>(Arrays.asList(clazz.getDeclaredFields()));

        if (clazz.getSuperclass() != null) {
            fields.addAll(getAllFields(clazz.getSuperclass()));
        }

        return fields;
    }

    /**
     * Проверяет, следует ли пропустить поле при сопоставлении
     *
     * @param field поле
     * @return true, если поле следует пропустить
     */
    public static boolean shouldSkipField(Field field) {
        try {
            field.setAccessible(true);

            // Поиск аннотации FieldDescription
            Annotation fieldDescAnnotation = findFieldDescriptionAnnotation(field);

            if (fieldDescAnnotation != null) {
                Boolean skipMapping = getAnnotationValue(fieldDescAnnotation, "skipMapping");
                return skipMapping != null && skipMapping;
            }
        } catch (Exception e) {
            log.error("Error checking if field {} should be skipped: {}", field.getName(), e.getMessage());
        }

        return false;
    }
}