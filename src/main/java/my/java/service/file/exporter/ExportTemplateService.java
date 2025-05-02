// src/main/java/my/java/service/export/ExportTemplateService.java
package my.java.service.file.exporter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.Client;
import my.java.model.ExportTemplate;
import my.java.repository.ExportTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

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
        if (template.isDefault()) {
            // Если делаем шаблон по умолчанию, сбрасываем флаг у других шаблонов
            templateRepository.findByClientIdAndEntityTypeAndIsDefaultTrue(
                            template.getClient().getId(), template.getEntityType())
                    .ifPresent(existingDefault -> {
                        existingDefault.setDefault(false);
                        templateRepository.save(existingDefault);
                    });
        }
        template.setUpdatedAt(ZonedDateTime.now());
        return templateRepository.save(template);
    }

    /**
     * Удаление шаблона
     */
    @Transactional
    public void deleteTemplate(Long id) {
        templateRepository.deleteById(id);
    }

    /**
     * Создание копии последних настроек экспорта как шаблона
     */
    @Transactional
    public ExportTemplate createFromLastExport(Client client, String entityType,
                                               List<String> fields, String fileType,
                                               String strategyId, String fileOptions) {
        ExportTemplate template = new ExportTemplate();
        template.setClient(client);
        template.setEntityType(entityType);
        template.setFileType(fileType);
        template.setName("Последний экспорт " + ZonedDateTime.now());
        template.setStrategyId(strategyId);
        template.setFileOptions(fileOptions);

        // Добавляем поля
        for (String field : fields) {
            ExportTemplate.ExportField exportField = new ExportTemplate.ExportField();
            exportField.setOriginalField(field);
            exportField.setDisplayName(getDefaultDisplayName(field));
            template.getFields().add(exportField);
        }

        return templateRepository.save(template);
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