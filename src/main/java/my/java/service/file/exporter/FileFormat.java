package my.java.service.file.exporter;

import org.springframework.http.MediaType;

/**
 * Перечисление поддерживаемых форматов экспорта
 * Путь: /java/my/java/service/file/exporter/FileFormat.java
 */
public enum FileFormat {
    CSV("csv", "text/csv"),
    EXCEL("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    JSON("json", "application/json"),
    XML("xml", "application/xml");

    private final String extension;
    private final String contentType;

    FileFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String getExtension() {
        return extension;
    }

    public String getContentType() {
        return contentType;
    }

    public MediaType getMediaType() {
        return MediaType.parseMediaType(contentType);
    }

    /**
     * Получает формат файла по строковому представлению
     * @param format строковое представление формата
     * @return формат файла или CSV по умолчанию
     */
    public static FileFormat fromString(String format) {
        if (format == null || format.isEmpty()) {
            return CSV;
        }

        try {
            return valueOf(format.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Если формат не найден, возвращаем CSV по умолчанию
            return CSV;
        }
    }
}