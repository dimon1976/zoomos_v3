// src/main/java/my/java/service/file/util/ImportParametersUtil.java
package my.java.service.file.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Утилитный класс для работы с параметрами импорта файлов.
 */
public class ImportParametersUtil {

    /**
     * Получает булево значение параметра из карты.
     *
     * @param params карта параметров
     * @param key ключ параметра
     * @param defaultValue значение по умолчанию
     * @return значение параметра или значение по умолчанию
     */
    public static boolean getBooleanParam(Map<String, String> params, String key, boolean defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(params.get(key));
    }

    /**
     * Получает целочисленное значение параметра из карты.
     *
     * @param params карта параметров
     * @param key ключ параметра
     * @param defaultValue значение по умолчанию
     * @return значение параметра или значение по умолчанию
     */
    public static int getIntParam(Map<String, String> params, String key, int defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(params.get(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Получает строковое значение параметра из карты.
     *
     * @param params карта параметров
     * @param key ключ параметра
     * @param defaultValue значение по умолчанию
     * @return значение параметра или значение по умолчанию
     */
    public static String getStringParam(Map<String, String> params, String key, String defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }
        return params.get(key);
    }

    /**
     * Создает копию карты параметров.
     *
     * @param params исходная карта параметров
     * @return новая карта параметров
     */
    public static Map<String, String> copyParams(Map<String, String> params) {
        return params != null ? new HashMap<>(params) : new HashMap<>();
    }
}