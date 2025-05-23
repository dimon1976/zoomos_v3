// src/main/java/my/java/service/file/exporter/ExportTemplateService.java
package my.java.service.file.exporter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.Client;
import my.java.model.ExportTemplate;
import my.java.repository.ExportTemplateRepository;
import my.java.service.file.options.FileWritingOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExportTemplateService {

    private final ExportTemplateRepository templateRepository;

    /**
     * Получение всех шаблонов экспорта для клиента
     */
    public List<ExportTemplate> getAllTemplatesForClient(Long clientId) {
        return templateRepository.findByClientIdOrderByCreatedAtDesc(clientId);
    }

    /**
     * Получение шаблонов для клиента и типа сущности
     */
    public List<ExportTemplate> getTemplatesForEntityType(Long clientId, String entityType) {
        return templateRepository.findByClientIdAndEntityTypeOrderByCreatedAtDesc(clientId, entityType);
    }

    /**
     * Получение шаблона по умолчанию
     */
    public Optional<ExportTemplate> getDefaultTemplate(Long clientId, String entityType) {
        return templateRepository.findByClientIdAndEntityTypeAndIsDefaultTrue(clientId, entityType);
    }

    /**
     * Получение шаблона по ID
     */
    public Optional<ExportTemplate> getTemplateById(Long id) {
        return templateRepository.findById(id);
    }

    /**
     * Сохранение шаблона
     */
    @Transactional
    public ExportTemplate saveTemplate(ExportTemplate template) {
        log.info("Сохранение шаблона: {}", template.getName());

        // Если это новый шаблон, устанавливаем дату создания
        if (template.getId() == null) {
            template.setCreatedAt(ZonedDateTime.now());
            log.debug("Создание нового шаблона с датой: {}", template.getCreatedAt());
        }

        // Обновляем дату изменения
        template.setUpdatedAt(ZonedDateTime.now());

        // Если делаем шаблон по умолчанию, сбрасываем флаг у других шаблонов
        if (template.isDefault()) {
            log.debug("Шаблон помечен как 'по умолчанию', сбрасываем флаг у других шаблонов");
            templateRepository.findByClientIdAndEntityTypeAndIsDefaultTrue(
                            template.getClient().getId(), template.getEntityType())
                    .ifPresent(existingDefault -> {
                        if (!existingDefault.getId().equals(template.getId())) {
                            log.debug("Сброс флага 'по умолчанию' у шаблона: {}", existingDefault.getName());
                            existingDefault.setDefault(false);
                            templateRepository.save(existingDefault);
                        }
                    });
        }

        // Проверяем поля шаблона перед сохранением
        if (template.getFields() == null || template.getFields().isEmpty()) {
            log.warn("Шаблон {} не содержит полей", template.getName());
        } else {
            log.debug("Шаблон содержит {} полей", template.getFields().size());
        }

        // Проверяем настройки экспорта
        if (template.getExportOptions() == null) {
            log.debug("Шаблон не содержит настроек экспорта, создаем базовые параметры");
            FileWritingOptions options = new FileWritingOptions();
            options.setFileType(template.getFileType() != null ? template.getFileType() : "csv");
            template.setExportOptions(options);
        } else {
            log.debug("Шаблон содержит настройки экспорта: {}", template.getExportOptions());
        }

        // Сохраняем шаблон
        ExportTemplate savedTemplate = templateRepository.save(template);
        log.info("Шаблон успешно сохранен: id={}, name={}", savedTemplate.getId(), savedTemplate.getName());

        return savedTemplate;
    }

    /**
     * Удаление шаблона
     */
    @Transactional
    public void deleteTemplate(Long id) {
        Optional<ExportTemplate> template = templateRepository.findById(id);
        if (template.isPresent()) {
            // Если удаляемый шаблон был по умолчанию, нужно сделать другой шаблон по умолчанию
            if (template.get().isDefault()) {
                // Находим другой шаблон для того же типа сущности
                List<ExportTemplate> otherTemplates = templateRepository.findByClientIdAndEntityTypeOrderByCreatedAtDesc(
                                template.get().getClient().getId(), template.get().getEntityType()).stream()
                        .filter(t -> !t.getId().equals(id))
                        .collect(Collectors.toList());

                if (!otherTemplates.isEmpty()) {
                    // Делаем первый найденный шаблон шаблоном по умолчанию
                    ExportTemplate newDefault = otherTemplates.get(0);
                    newDefault.setDefault(true);
                    templateRepository.save(newDefault);
                    log.info("Шаблон {} установлен как новый шаблон по умолчанию после удаления", newDefault.getName());
                }
            }

            templateRepository.deleteById(id);
            log.info("Шаблон с ID {} успешно удален", id);
        } else {
            log.warn("Попытка удаления несуществующего шаблона с ID {}", id);
        }
    }

    /**
     * Обновление шаблона
     */
    @Transactional
    public ExportTemplate updateTemplate(Long id, ExportTemplate updatedTemplate) {
        log.info("Обновление шаблона с ID: {}", id);

        ExportTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Шаблон с ID {} не найден", id);
                    return new IllegalArgumentException("Шаблон с ID " + id + " не найден");
                });

        log.debug("Найден шаблон для обновления: {}, fields.size={}",
                template.getName(), template.getFields().size());

        // Обновляем основные поля шаблона
        template.setName(updatedTemplate.getName());
        if (updatedTemplate.getDescription() != null) {
            template.setDescription(updatedTemplate.getDescription());
        }
        template.setDefault(updatedTemplate.isDefault());
        template.setStrategyId(updatedTemplate.getStrategyId());

        // Обновляем настройки экспорта
        if (updatedTemplate.getExportOptions() != null) {
            template.setExportOptions(updatedTemplate.getExportOptions());
            log.debug("Обновлены настройки экспорта");
        }

        // НЕ ИСПОЛЬЗУЕМ clear() и addAll() для коллекции fields, т.к. при @ElementCollection это может вызвать проблемы
        if (updatedTemplate.getFields() != null && !updatedTemplate.getFields().isEmpty()) {
            log.debug("Обновление полей шаблона: {} -> {}",
                    template.getFields().size(),
                    updatedTemplate.getFields().size());

            // РЕШЕНИЕ: Вместо template.getFields().clear() и template.getFields().addAll()
            // устанавливаем поля через сеттер ПОСЛЕ обнуления их через сеттер
            List<ExportTemplate.ExportField> newFields = new ArrayList<>(updatedTemplate.getFields());
            template.getFields().clear();
            template.getFields().addAll(newFields);
        } else {
            log.warn("Переданный шаблон не содержит полей! Сохраняем существующие: {}",
                    template.getFields().size());
        }

        // Обновляем время изменения
        template.setUpdatedAt(ZonedDateTime.now());

        // Выполняем saveAndFlush для немедленной записи в БД
        ExportTemplate savedTemplate = templateRepository.saveAndFlush(template);
        log.debug("После сохранения: fields.size={}", savedTemplate.getFields().size());

        return savedTemplate;
    }

    /**
     * Создание шаблона из настроек экспорта
     */
    @Transactional
    public ExportTemplate createFromExportSettings(Client client, String entityType,
                                                   List<String> fields, FileWritingOptions options,
                                                   Map<String, String> headerMap) {
        ExportTemplate template = new ExportTemplate();
        template.setClient(client);
        template.setEntityType(entityType);
        template.setName("Экспорт " + ZonedDateTime.now().toLocalDateTime());

        // Устанавливаем настройки экспорта
        template.setExportOptions(options);

        // Устанавливаем стратегию
        if (options.getAdditionalParams().containsKey("strategyId")) {
            template.setStrategyId(options.getAdditionalParams().get("strategyId"));
        }

        // Добавляем поля
        List<ExportTemplate.ExportField> exportFields = new ArrayList<>();
        for (String field : fields) {
            ExportTemplate.ExportField exportField = new ExportTemplate.ExportField();
            exportField.setOriginalField(field);

            // Используем заголовок из параметров, если есть
            String headerKey = "header_" + field.replace(".", "_");
            if (headerMap.containsKey(headerKey)) {
                exportField.setDisplayName(headerMap.get(headerKey));
            } else {
                exportField.setDisplayName(getDefaultDisplayName(field));
            }

            exportFields.add(exportField);
        }

        template.setFields(exportFields);

        return templateRepository.save(template);
    }

    /**
     * Установка шаблона по умолчанию
     */
    @Transactional
    public ExportTemplate setDefaultTemplate(Long templateId) {
        return templateRepository.findById(templateId)
                .map(template -> {
                    // Сбрасываем флаг у текущего шаблона по умолчанию
                    templateRepository.findByClientIdAndEntityTypeAndIsDefaultTrue(
                                    template.getClient().getId(), template.getEntityType())
                            .ifPresent(currentDefault -> {
                                if (!currentDefault.getId().equals(templateId)) {
                                    currentDefault.setDefault(false);
                                    templateRepository.save(currentDefault);
                                }
                            });

                    // Устанавливаем новый шаблон по умолчанию
                    template.setDefault(true);
                    template.setUpdatedAt(ZonedDateTime.now());

                    return templateRepository.save(template);
                })
                .orElseThrow(() -> new IllegalArgumentException("Шаблон с ID " + templateId + " не найден"));
    }

    /**
     * Получение отображаемого имени поля по умолчанию
     */
    private String getDefaultDisplayName(String field) {
        // Отделяем префикс сущности, если есть
        int dotIndex = field.lastIndexOf('.');
        if (dotIndex > 0) {
            field = field.substring(dotIndex + 1);
        }

        // Преобразуем camelCase в читаемый текст
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (i == 0) {
                result.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c)) {
                result.append(' ').append(c);
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }
}