package my.java.service.export;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.exception.FileOperationException;
import my.java.model.Client;
import my.java.model.FileOperation;
import my.java.model.export.ExportTemplate;
import my.java.repository.FileOperationRepository;
import my.java.service.client.ClientService;
import my.java.service.export.strategy.ExportStrategy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExportService {

    private final List<ExportStrategy> exportStrategies;
    private final my.java.service.export.ExportTemplateService templateService;
    private final ClientService clientService;
    private final FileOperationRepository fileOperationRepository;

    /**
     * Запускает процесс экспорта с использованием шаблона
     */
    @Transactional
    public FileOperation initiateExport(Long clientId, Long templateId, Map<String, String> additionalParams) {
        log.info("Инициация экспорта для клиента {}, шаблон {}", clientId, templateId);

        // Получаем клиента
        Client client = clientService.findClientEntityById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клиент с ID " + clientId + " не найден"));

        // Получаем шаблон
        ExportTemplate template = templateService.getTemplateById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Шаблон с ID " + templateId + " не найден"));

        // Проверяем, что шаблон принадлежит клиенту
        if (!template.getClient().getId().equals(clientId)) {
            throw new IllegalArgumentException("Шаблон не принадлежит данному клиенту");
        }

        // Добавляем дополнительные параметры к параметрам шаблона
        if (additionalParams != null) {
            template.getParameters().putAll(additionalParams);
        }

        // Создаем запись об операции экспорта
        FileOperation operation = new FileOperation();
        operation.setClient(client);
        operation.setOperationType(FileOperation.OperationType.EXPORT);
        operation.setFileType(template.getFormat().toUpperCase());
        operation.setStatus(FileOperation.OperationStatus.PENDING);
        operation.setFileName("export_" + client.getId() + "_" + System.currentTimeMillis() +
                "." + template.getFormat());

        // Сохраняем операцию
        FileOperation savedOperation = fileOperationRepository.save(operation);

        // Отмечаем шаблон как использованный
        templateService.markTemplateAsUsed(templateId);

        // Асинхронно выполняем экспорт
        startAsyncExport(client, template, savedOperation);

        return savedOperation;
    }

    /**
     * Запускает процесс экспорта с использованием параметров (без шаблона)
     */
    @Transactional
    public FileOperation initiateExport(Long clientId, String entityType, String format,
                                        Map<String, String> fieldMapping, Map<String, String> parameters,
                                        String filterCondition) {
        log.info("Инициация экспорта для клиента {}, тип сущности {}", clientId, entityType);

        // Получаем клиента
        Client client = clientService.findClientEntityById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Клиент с ID " + clientId + " не найден"));

        // Создаем временный шаблон (не сохраняем в БД)
        ExportTemplate template = new ExportTemplate();
        template.setClient(client);
        template.setEntityType(entityType);
        template.setFormat(format);
        template.setFieldMapping(fieldMapping);
        template.setParameters(parameters);
        template.setFilterCondition(filterCondition);

        // Создаем запись об операции экспорта
        FileOperation operation = new FileOperation();
        operation.setClient(client);
        operation.setOperationType(FileOperation.OperationType.EXPORT);
        operation.setFileType(format.toUpperCase());
        operation.setStatus(FileOperation.OperationStatus.PENDING);
        operation.setFileName("export_" + client.getId() + "_" + System.currentTimeMillis() + "." + format);

        // Сохраняем операцию
        FileOperation savedOperation = fileOperationRepository.save(operation);

        // Асинхронно выполняем экспорт
        startAsyncExport(client, template, savedOperation);

        return savedOperation;
    }

    /**
     * Запускает асинхронное выполнение экспорта
     */
    @Async
    protected void startAsyncExport(Client client, ExportTemplate template, FileOperation operation) {
        CompletableFuture.runAsync(() -> executeExport(client, template, operation))
                .exceptionally(ex -> {
                    log.error("Ошибка при асинхронном выполнении экспорта: {}", ex.getMessage(), ex);
                    operation.markAsFailed("Непредвиденная ошибка: " + ex.getMessage());
                    fileOperationRepository.save(operation);
                    return null;
                });
    }

    /**
     * Выполняет экспорт данных с использованием подходящей стратегии
     */
    protected void executeExport(Client client, ExportTemplate template, FileOperation operation) {
        try {
            log.info("Выполнение экспорта для клиента {}, операция {}", client.getId(), operation.getId());

            // Выбираем подходящую стратегию
            ExportStrategy strategy = findSuitableStrategy(template.getEntityType(), template.getParameters());

            if (strategy == null) {
                throw new FileOperationException("Не найдена подходящая стратегия для экспорта");
            }

            // Проверяем, поддерживает ли стратегия выбранный формат
            boolean formatSupported = false;
            for (String supportedFormat : strategy.getSupportedFormats()) {
                if (supportedFormat.equalsIgnoreCase(template.getFormat())) {
                    formatSupported = true;
                    break;
                }
            }

            if (!formatSupported) {
                throw new FileOperationException("Формат " + template.getFormat() +
                        " не поддерживается выбранной стратегией экспорта");
            }

            // Выполняем экспорт
            Path exportedFilePath = strategy.executeExport(client, template, operation);

            // Обновляем операцию с результатом
            operation.setResultFilePath(exportedFilePath.toString());
            fileOperationRepository.save(operation);

            log.info("Экспорт успешно завершен, операция {}", operation.getId());

        } catch (Exception e) {
            log.error("Ошибка при выполнении экспорта: {}", e.getMessage(), e);
            operation.markAsFailed("Ошибка экспорта: " + e.getMessage());
            fileOperationRepository.save(operation);
        }
    }

    /**
     * Находит подходящую стратегию для экспорта
     */
    private ExportStrategy findSuitableStrategy(String entityType, Map<String, String> parameters) {
        Optional<ExportStrategy> suitableStrategy = exportStrategies.stream()
                .filter(strategy -> strategy.isApplicable(entityType, parameters))
                .findFirst();

        return suitableStrategy.orElse(null);
    }
}