package my.java.service.file.importer;

/**
 * Стратегии обработки дубликатов
 */
public enum DuplicateStrategy {
    SKIP,     // Пропускать дубликаты (записывать информацию для анализа)
    OVERRIDE, // Обновлять существующие записи
    IGNORE    // Игнорировать проверку дубликатов (записывать все)
}
