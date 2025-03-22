package my.java.service.file.transformer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ValueTransformerFactory {

    private final Map<Class<?>, ValueTransformer<?>> transformers = new HashMap<>();
    private final ValueTransformer<String> defaultTransformer;

    @Autowired
    public ValueTransformerFactory(List<ValueTransformer<?>> transformerList) {
        registerTransformers(transformerList);
        defaultTransformer = getTransformerForType(new StringTransformer());
    }
    @SuppressWarnings("unchecked")
    private <T> ValueTransformer<T> getTransformerForType(ValueTransformer<T> defaultValue) {
        ValueTransformer<?> transformer = transformers.get(String.class);
        if (transformer != null && String.class.isAssignableFrom(transformer.getTargetType())) {
            return (ValueTransformer<T>) transformer;
        }
        return defaultValue;
    }

    private void registerTransformers(List<ValueTransformer<?>> transformerList) {
        transformerList.forEach(transformer -> {
            log.debug("Registering transformer for type: {}", transformer.getTargetType().getName());
            transformers.put(transformer.getTargetType(), transformer);
        });
    }

    @SuppressWarnings("unchecked")
    public <T> ValueTransformer<T> getTransformer(Class<T> targetType) {
        if (targetType == null) {
            return (ValueTransformer<T>) defaultTransformer;
        }

        if (targetType.isEnum()) {
            return (ValueTransformer<T>) transformers.get(Enum.class);
        }

        if (targetType.isPrimitive()) {
            targetType = (Class<T>) mapPrimitiveToWrapper(targetType);
        }

        return (ValueTransformer<T>) transformers.getOrDefault(targetType, defaultTransformer);
    }

    @SuppressWarnings("unchecked")
    public <T> T transform(String value, Class<T> targetType, String params) {
        if (targetType == null) {
            return (T) getTransformer(String.class).transform(value, params);
        }

        if (targetType.isPrimitive()) {
            targetType = (Class<T>) mapPrimitiveToWrapper(targetType);
        }

        if (targetType.isEnum()) {
            String enumParams = "class=" + targetType.getName();
            if (params != null && !params.isEmpty()) {
                enumParams += "|" + params;
            }

            ValueTransformer<Enum> enumTransformer = getTransformer(Enum.class);
            Enum result = enumTransformer.transform(value, enumParams);
            return (T) result;
        }

        ValueTransformer<T> transformer = getTransformer(targetType);
        return transformer.transform(value, params);
    }

    public <T> boolean canTransform(String value, Class<T> targetType, String params) {
        if (targetType == null) {
            return getTransformer(String.class).canTransform(value, params);
        }

        if (targetType.isPrimitive()) {
            targetType = (Class<T>) mapPrimitiveToWrapper(targetType);
        }

        if (targetType.isEnum()) {
            String enumParams = "class=" + targetType.getName();
            if (params != null && !params.isEmpty()) {
                enumParams += "|" + params;
            }

            ValueTransformer<Enum> enumTransformer = getTransformer(Enum.class);
            return enumTransformer.canTransform(value, enumParams);
        }

        ValueTransformer<T> transformer = getTransformer(targetType);
        return transformer.canTransform(value, params);
    }

    @SuppressWarnings("unchecked")
    public <T> String toString(T value, String params) {
        if (value == null) {
            return "";
        }

        Class<?> valueType = value.getClass();

        if (valueType.isEnum()) {
            String enumParams = "class=" + valueType.getName();
            if (params != null && !params.isEmpty()) {
                enumParams += "|" + params;
            }

            ValueTransformer<Enum> enumTransformer = getTransformer(Enum.class);
            return enumTransformer.toString((Enum) value, enumParams);
        }

        ValueTransformer<T> transformer = (ValueTransformer<T>) getTransformer((Class<T>) valueType);
        return transformer.toString(value, params);
    }

    private Class<?> mapPrimitiveToWrapper(Class<?> primitiveType) {
        if (primitiveType == int.class) return Integer.class;
        if (primitiveType == long.class) return Long.class;
        if (primitiveType == double.class) return Double.class;
        if (primitiveType == float.class) return Float.class;
        if (primitiveType == boolean.class) return Boolean.class;
        if (primitiveType == char.class) return Character.class;
        if (primitiveType == byte.class) return Byte.class;
        if (primitiveType == short.class) return Short.class;
        if (primitiveType == void.class) return Void.class;
        return primitiveType;
    }
}