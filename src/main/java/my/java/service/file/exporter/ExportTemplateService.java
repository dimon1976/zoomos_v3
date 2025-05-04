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
        // Если это новый шаблон, устанавливаем дату создания
        if (template.getId() == null) {
            template.setCreatedAt(ZonedDateTime.now());
        }

        // Обновляем дату изменения
        template.setUpdatedAt(ZonedDateTime.now());

        // Если делаем шаблон по умолчанию, сбрасываем флаг у других шаблонов
        if (template.isDefault()) {
            templateRepository.findByClientIdAndEntityTypeAndIsDefaultTrue(
                            template.getClient().getId(), template.getEntityType())
                    .ifPresent(existingDefault -> {
                        if (!existingDefault.getId().equals(template.getId())) {
                            existingDefault.setDefault(false);
                            templateRepository.save(existingDefault);
                        }
                    });
        }

        // Проверяем поля шаблона перед сохранением
        if (template.getFields() == null || template.getFields().isEmpty()) {
            log.warn("Шаблон {} не содержит полей", template.getName());
        }

        return templateRepository.save(template);
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
        return templateRepository.findById(id)
                .map(template -> {
                    // Обновляем основные поля шаблона
                    template.setName(updatedTemplate.getName());
                    template.setDescription(updatedTemplate.getDescription());
                    template.setFileType(updatedTemplate.getFileType());
                    template.setStrategyId(updatedTemplate.getStrategyId());
                    template.setDefault(updatedTemplate.isDefault());

                    // Устанавливаем новые поля
                    template.getFields().clear();
                    template.getFields().addAll(updatedTemplate.getFields());

                    // Обновляем параметры файла
                    template.setFileOptions(updatedTemplate.getFileOptions());

                    // Обновляем время изменения
                    template.setUpdatedAt(ZonedDateTime.now());

                    return saveTemplate(template);
                })
                .orElseThrow(() -> new IllegalArgumentException("Шаблон с ID " + id + " не найден"));
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