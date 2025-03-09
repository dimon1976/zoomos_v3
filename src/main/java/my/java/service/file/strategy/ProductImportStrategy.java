package my.java.service.file.strategy;


import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import my.java.model.FileOperation;
import my.java.model.entity.Product;
import my.java.model.enums.DataSourceType;
import my.java.service.file.FieldMappingService;
import my.java.service.file.FileProcessingService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Стратегия импорта данных для сущности Product
 */
@Component
@Slf4j
public class ProductImportStrategy extends BaseImportStrategy<Product> {

    @PersistenceContext
    private EntityManager entityManager;

    public ProductImportStrategy(FieldMappingService fieldMappingService) {
        super(fieldMappingService, Product.class);
    }

    @Override
    public String getName() {
        return "ProductImport";
    }

    @Override
    public String getDescription() {
        return "Импорт данных товаров";
    }

    @Override
    protected String validateEntity(Product entity, Map<String, String> rowData) {
        // Проверяем обязательные поля
        if (entity.getProductName() == null || entity.getProductName().trim().isEmpty()) {
            return "Не указано название товара";
        }

        // Дополнительные проверки могут быть добавлены здесь

        return null; // Валидация успешна
    }

    @Override
    @Transactional
    public void saveEntities(List<Product> entities, Long operationId, Map<String, Object> params) {
        log.debug("Saving {} products for operation {}", entities.size(), operationId);

        // Получаем clientId из параметров
        Long clientId = getClientId(params);

        // Получаем источник данных из параметров или используем значение по умолчанию
        DataSourceType dataSourceType = getDataSourceType(params);

        // Получаем fileId из параметров
        Long fileId = getFileId(params);

        // Устанавливаем дополнительные поля для каждой сущности
        for (Product product : entities) {
            // Устанавливаем clientId
            if (clientId != null) {
                product.setClientId(clientId);
            }

            // Устанавливаем источник данных
            product.setDataSource(dataSourceType);

            // Устанавливаем fileId
            if (fileId != null) {
                product.setFileId(fileId);
            }

            // Сохраняем сущность
            entityManager.persist(product);
        }

        // Очищаем кэш для уменьшения потребления памяти
        entityManager.flush();
        entityManager.clear();
    }

    @Override
    @Transactional
    public void rollback(Long operationId, Map<String, Object> params) {
        // Получаем fileId из параметров
        Long fileId = getFileId(params);

        if (fileId != null) {
            // Удаляем все данные, связанные с этим файлом
            log.debug("Rolling back products import for operation {}, fileId {}", operationId, fileId);

            // Удаляем связанные данные (RegionData и CompetitorData)
            entityManager.createQuery("DELETE FROM RegionData rd WHERE rd.product.fileId = :fileId")
                    .setParameter("fileId", fileId)
                    .executeUpdate();

            entityManager.createQuery("DELETE FROM CompetitorData cd WHERE cd.product.fileId = :fileId")
                    .setParameter("fileId", fileId)
                    .executeUpdate();

            // Удаляем товары
            entityManager.createQuery("DELETE FROM Product p WHERE p.fileId = :fileId")
                    .setParameter("fileId", fileId)
                    .executeUpdate();
        } else {
            log.warn("Cannot rollback product import: fileId not provided in params");
        }
    }

    @Override
    public FileProcessingService.FileProcessingResult finishProcessing(Long operationId, Map<String, Object> params) {
        log.debug("Finishing product import for operation {}", operationId);

        // Создаем результат обработки
        FileProcessingService.FileProcessingResult result = new FileProcessingService.FileProcessingResult();
        result.setOperationId(operationId);
        result.setStatus(FileOperation.OperationStatus.COMPLETED);
        result.setMessage("Импорт товаров успешно завершен");

        return result;
    }

    /**
     * Получает идентификатор клиента из параметров
     *
     * @param params параметры стратегии
     * @return идентификатор клиента или null, если не указан
     */
    private Long getClientId(Map<String, Object> params) {
        if (params != null && params.containsKey("clientId")) {
            try {
                return Long.valueOf(params.get("clientId").toString());
            } catch (NumberFormatException e) {
                log.warn("Invalid clientId in params: {}", params.get("clientId"));
            }
        }
        return null;
    }

    /**
     * Получает источник данных из параметров
     *
     * @param params параметры стратегии
     * @return источник данных или FILE по умолчанию
     */
    private DataSourceType getDataSourceType(Map<String, Object> params) {
        if (params != null && params.containsKey("dataSourceType")) {
            try {
                return DataSourceType.valueOf(params.get("dataSourceType").toString());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid dataSourceType in params: {}", params.get("dataSourceType"));
            }
        }
        return DataSourceType.FILE; // По умолчанию
    }

    /**
     * Получает идентификатор файла из параметров
     *
     * @param params параметры стратегии
     * @return идентификатор файла или null, если не указан
     */
    private Long getFileId(Map<String, Object> params) {
        if (params != null && params.containsKey("fileId")) {
            try {
                return Long.valueOf(params.get("fileId").toString());
            } catch (NumberFormatException e) {
                log.warn("Invalid fileId in params: {}", params.get("fileId"));
            }
        }
        return null;
    }
}