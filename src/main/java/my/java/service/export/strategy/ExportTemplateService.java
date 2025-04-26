package my.java.service.export;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.Client;
import my.java.model.export.ExportTemplate;
import my.java.repository.ExportTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExportTemplateService {

    private final ExportTemplateRepository templateRepository;

    /**
     * Получает шаблон по ID
     */
    @Transactional(readOnly = true)
    public Optional<ExportTemplate> getTemplateById(Long id) {
        return templateRepository.findById(id);
    }

    /**
     * Получает список шаблонов для клиента и типа сущности
     */
    @Transactional(readOnly = true)
    public List<ExportTemplate> getTemplatesForClientAndEntityType(Long clientId, String entityType) {
        return templateRepository.findByClientIdAndEntityTypeAndActiveTrue(clientId, entityType);
    }

    /**
     * Получает список недавно использованных шаблонов клиента
     */
    @Transactional(readOnly = true)
    public List<ExportTemplate> getRecentTemplates(Long clientId) {
        return templateRepository.findRecentTemplatesByClientId(clientId);
    }

    /**
     * Создает новый шаблон экспорта
     */
    @Transactional
    public ExportTemplate createTemplate(String name, String description, Client client,
                                         String entityType, String format,
                                         Map<String, String> fieldMapping,
                                         Map<String, String> parameters,
                                         String filterCondition) {

        // Проверка существования шаблона с таким именем
        if (templateRepository.existsByNameAndClientIdAndEntityType(name, client.getId(), entityType)) {
            throw new IllegalArgumentException("Шаблон с именем '" + name + "' уже существует");
        }

        ExportTemplate template = new ExportTemplate();
        template.setName(name);
        template.setDescription(description);
        template.setClient(client);
        template.setEntityType(entityType);
        template.setFormat(format);
        template.setFieldMapping(fieldMapping);
        template.setParameters(parameters);
        template.setFilterCondition(filterCondition);
        template.setCreatedAt(ZonedDateTime.now());
        template.setUpdatedAt(ZonedDateTime.now());
        template.setActive(true);

        return templateRepository.save(template);
    }

    /**
     * Обновляет существующий шаблон
     */
    @Transactional
    public ExportTemplate updateTemplate(Long id, String name, String description,
                                         String format, Map<String, String> fieldMapping,
                                         Map<String, String> parameters, String filterCondition) {

        ExportTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Шаблон с ID " + id + " не найден"));

        template.setName(name);
        template.setDescription(description);
        template.setFormat(format);
        template.setFieldMapping(fieldMapping);
        template.setParameters(parameters);
        template.setFilterCondition(filterCondition);
        template.setUpdatedAt(ZonedDateTime.now());

        return templateRepository.save(template);
    }

    /**
     * Удаляет шаблон (мягкое удаление)
     */
    @Transactional
    public void deleteTemplate(Long id) {
        ExportTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Шаблон с ID " + id + " не найден"));

        template.setActive(false);
        template.setUpdatedAt(ZonedDateTime.now());
        templateRepository.save(template);
    }

    /**
     * Отмечает шаблон как использованный
     */
    @Transactional
    public void markTemplateAsUsed(Long id) {
        templateRepository.findById(id).ifPresent(template -> {
            template.markAsUsed();
            templateRepository.save(template);
        });
    }
}