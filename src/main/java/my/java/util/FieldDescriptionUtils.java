package my.java.util;

import lombok.extern.slf4j.Slf4j;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Утилитный класс для работы с аннотацией FieldDescription.
 * Предоставляет методы для получения описаний полей из аннотаций.
 */
@Slf4j
public final class FieldDescriptionUtils {

    private static final String ANNOTATION_NAME = "FieldDescription";
    private static final String ANNOTATION_VALUE_METHOD = "value";
    private static final String ANNOTATION_SKIP_MAPPING_METHOD = "skipMapping";

    /**
     * Приватный конструктор для предотвращения создания экземпляров утилитного класса.
     */
    private FieldDescriptionUtils() {
        throw new AssertionError("Утилитный класс не должен быть инстанцирован");
    }

    /**
     * Получает сопоставление полей сущности с их описаниями из аннотаций FieldDescription.
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
                    addFieldDescriptionToMap(result, field, fieldDescAnnotation);
                }
            } catch (Exception e) {
                log.error("Ошибка при получении описания поля {}: {}", field.getName(), e.getMessage());
            }
        }

        return result;
    }

    /**
     * Добавляет описание поля в сопоставление, если поле не должно быть пропущено.
     *
     * @param resultMap сопоставление имен полей и их описаний
     * @param field поле
     * @param annotation аннотация FieldDescription
     */
    private static void addFieldDescriptionToMap(Map<String, String> resultMap, Field field,
                                                 Annotation annotation) {
        String value = getAnnotationValue(annotation, ANNOTATION_VALUE_METHOD);
        Boolean skipMapping = getAnnotationValue(annotation, ANNOTATION_SKIP_MAPPING_METHOD);

        if (skipMapping == null || !skipMapping) {
            resultMap.put(field.getName(), value);
        }
    }

    /**
     * Находит аннотацию FieldDescription в любом пакете.
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
     * Получает значение атрибута аннотации через рефлексию.
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
            log.debug("Не удалось получить атрибут аннотации {}: {}", attributeName, e.getMessage());
            return null;
        }
    }

    /**
     * Получает сопоставление описаний полей и их имен в сущности.
     *
     * @param entityClass класс сущности
     * @return Map, где ключ - описание из аннотации, значение - имя поля
     */
    public static Map<String, String> getDescriptionToFieldMap(Class<?> entityClass) {
        return getFieldDescriptions(entityClass).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (first, second) -> first));
    }

    /**
     * Получает все поля класса, включая поля его суперклассов.
     *
     * @param clazz класс
     * @return список полей
     */
    public static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>(Arrays.asList(clazz.getDeclaredFields()));

        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && !superClass.equals(Object.class)) {
            fields.addAll(getAllFields(superClass));
        }

        return fields;
    }

    /**
     * Проверяет, следует ли пропустить поле при сопоставлении.
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
                Boolean skipMapping = getAnnotationValue(fieldDescAnnotation, ANNOTATION_SKIP_MAPPING_METHOD);
                return skipMapping != null && skipMapping;
            }
        } catch (Exception e) {
            log.error("Ошибка при проверке необходимости пропуска поля {}: {}", field.getName(), e.getMessage());
        }

        return false;
    }
}