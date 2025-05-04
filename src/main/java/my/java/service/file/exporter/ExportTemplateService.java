// src/main/java/my/java/service/file/exporter/ExportTemplateService.java
package my.java.service.file.exporter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.Client;
import my.java.model.ExportTemplate;
import my.java.repository.ExportTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExportTemplateService {

    private final ExportTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

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

        // Проверяем наличие дополнительных параметров шаблона
        if (template.getFileOptions() == null || template.getFileOptions().isEmpty()) {
            log.debug("Шаблон не содержит параметров формата файла, создаем базовые параметры");
            try {
                // Создаем базовые параметры, если их нет
                Map<String, String> defaultOptions = new HashMap<>();
                defaultOptions.put("format", template.getFileType());
                defaultOptions.put("includeHeader", "true");

                if ("csv".equalsIgnoreCase(template.getFileType())) {
                    defaultOptions.put("delimiter", ",");
                    defaultOptions.put("quoteChar", "\"");
                    defaultOptions.put("encoding", "UTF-8");
                } else if ("xlsx".equalsIgnoreCase(template.getFileType())) {
                    defaultOptions.put("sheetName", "Data");
                    defaultOptions.put("autoSizeColumns", "true");
                }

                template.setFileOptions(objectMapper.writeValueAsString(defaultOptions));
            } catch (JsonProcessingException e) {
                log.warn("Не удалось создать параметры по умолчанию для шаблона: {}", e.getMessage());
            }
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

        return templateRepository.findById(id)
                .map(template -> {
                    log.debug("Найден шаблон для обновления: {}", template.getName());

                    // Обновляем основные поля шаблона
                    template.setName(updatedTemplate.getName());
                    template.setDescription(updatedTemplate.getDescription());
                    template.setFileType(updatedTemplate.getFileType());
                    template.setStrategyId(updatedTemplate.getStrategyId());
                    template.setDefault(updatedTemplate.isDefault());

                    // Устанавливаем новые поля
                    if (updatedTemplate.getFields() != null && !updatedTemplate.getFields().isEmpty()) {
                        log.debug("Обновление полей шаблона: {} -> {}",
                                template.getFields().size(),
                                updatedTemplate.getFields().size());
                        template.getFields().clear();
                        template.getFields().addAll(updatedTemplate.getFields());
                    } else {
                        log.warn("Не найдены поля для обновления в шаблоне");
                    }

                    // Обновляем параметры файла
                    if (updatedTemplate.getFileOptions() != null && !updatedTemplate.getFileOptions().isEmpty()) {
                        log.debug("Обновление параметров файла");
                        template.setFileOptions(updatedTemplate.getFileOptions());
                    } else {
                        log.debug("Отсутствуют параметры файла в обновляемом шаблоне");

                        // Попытка создать параметры из данных формы
                        try {
                            Map<String, String> fileOptions = new HashMap<>();
                            fileOptions.put("format", updatedTemplate.getFileType());

                            // Добавляем параметры стратегии
                            if (updatedTemplate.getStrategyId() != null) {
                                fileOptions.put("strategyId", updatedTemplate.getStrategyId());
                            }

                            // Обновляем параметры файла в JSON формате
                            template.setFileOptions(objectMapper.writeValueAsString(fileOptions));
                            log.debug("Созданы базовые параметры файла для шаблона");
                        } catch (Exception e) {
                            log.warn("Не удалось создать параметры файла: {}", e.getMessage());
                        }
                    }

                    // Обновляем время изменения
                    template.setUpdatedAt(ZonedDateTime.now());

                    // Сохраняем обновленный шаблон
                    ExportTemplate savedTemplate = saveTemplate(template);
                    log.info("Шаблон успешно обновлен: {}", savedTemplate.getName());
                    return savedTemplate;
                })
                .orElseThrow(() -> {
                    log.error("Шаблон с ID {} не найден", id);
                    return new IllegalArgumentException("Шаблон с ID " + id + " не найден");
                });
    }

    /**
     * Создание копии последних настроек экспорта как шаблона
     */
    @Transactional
    public ExportTemplate createFromLastExport(Client client, String entityType,
                                               List<String> fields, String fileType,
                                               String strategyId, Map<String, String> fileOptions) {
        ExportTemplate template = new ExportTemplate();
        template.setClient(client);
        template.setEntityType(entityType);
        template.setFileType(fileType);
        template.setName("Последний экспорт " + ZonedDateTime.now());
        template.setStrategyId(strategyId);

        // Сохраняем параметры файла как JSON с использованием ObjectMapper
        try {
            String fileOptionsJson = objectMapper.writeValueAsString(fileOptions);
            template.setFileOptions(fileOptionsJson);
        } catch (JsonProcessingException e) {
            log.warn("Не удалось сохранить параметры файла как JSON: {}", e.getMessage());
        }

        // Добавляем поля
        for (String field : fields) {
            ExportTemplate.ExportField exportField = new ExportTemplate.ExportField();
            exportField.setOriginalField(field);

            // Используем заголовок из параметров, если есть
            String headerKey = "header_" + field.replace(".", "_");
            if (fileOptions.containsKey(headerKey)) {
                exportField.setDisplayName(fileOptions.get(headerKey));
            } else {
                exportField.setDisplayName(getDefaultDisplayName(field));
            }

            template.getFields().add(exportField);
        }

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