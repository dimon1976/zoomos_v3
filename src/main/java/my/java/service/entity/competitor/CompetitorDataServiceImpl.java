// src/main/java/my/java/service/competitor/CompetitorDataServiceImpl.java
package my.java.service.entity.competitor;

import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.Competitor;
import my.java.repository.CompetitorDataRepository;
import my.java.service.base.BaseEntityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Реализация сервиса для управления данными конкурентов
 */
@Service
@Slf4j
public class CompetitorDataServiceImpl extends BaseEntityService<Competitor, Long, CompetitorDataRepository> implements CompetitorDataService {

    public CompetitorDataServiceImpl(CompetitorDataRepository repository) {
        super(repository);
    }

    @Override
    protected void logSave(Competitor entity) {
        log.debug("Сохранение данных конкурента: {}", entity.getCompetitorName());
    }

    @Override
    public Competitor saveCompetitorData(Competitor competitor) {
        return save(competitor);
    }

    @Override
    public int saveCompetitorDataList(List<Competitor> competitorList) {
        return saveAll(competitorList);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competitor> findByProductId(Long productId) {
        log.debug("Поиск данных конкурентов по productId: {}", productId);
        return repository.findByProductId(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competitor> findByClientId(Long clientId) {
        log.debug("Поиск данных конкурентов по clientId: {}", clientId);
        return repository.findByClientId(clientId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competitor> findByDateAfterAndClientId(LocalDateTime date, Long clientId) {
        log.debug("Поиск данных конкурентов по дате после {} и clientId: {}", date, clientId);
        return repository.findByCompetitorLocalDateTimeAfterAndClientId(date, clientId);
    }

    @Override
    public void deleteCompetitorData(Long id) {
        deleteById(id);
    }

    @Override
    @Transactional
    public int deleteByProductId(Long productId) {
        log.debug("Удаление данных конкурентов по productId: {}", productId);
        return deleteEntitiesByCondition(() -> repository.findByProductId(productId),
                () -> repository.deleteByProductId(productId));
    }

    @Override
    @Transactional
    public int deleteByClientId(Long clientId) {
        log.debug("Удаление данных конкурентов по clientId: {}", clientId);
        return deleteEntitiesByCondition(() -> repository.findByClientId(clientId),
                () -> repository.deleteByClientId(clientId));
    }

    /**
     * Удаляет сущности по условию
     *
     * @param findEntities поставщик списка сущностей для удаления
     * @param deleteOperation операция удаления
     * @return количество удаленных сущностей
     */
    private int deleteEntitiesByCondition(java.util.function.Supplier<List<Competitor>> findEntities,
                                          Runnable deleteOperation) {
        List<Competitor> recordsToDelete = findEntities.get();
        if (recordsToDelete.isEmpty()) {
            return 0;
        }

        deleteOperation.run();
        return recordsToDelete.size();
    }

    @Override
    @Transactional
    public Competitor upsertCompetitorData(Competitor competitor) {
        log.debug("Обновление/создание данных конкурента: {}", competitor.getCompetitorName());

        if (canFindExistingEntity(competitor)) {
            Optional<Competitor> existingData = repository.findByCompetitorNameAndProductId(
                    competitor.getCompetitorName(), competitor.getProduct().getId());

            if (existingData.isPresent()) {
                Competitor existing = existingData.get();
                copyCompetitorDataFields(competitor, existing);
                return repository.save(existing);
            }
        }

        return repository.save(competitor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competitor> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return repository.findAllById(ids);
    }

    /**
     * Проверяет возможность поиска существующей сущности
     *
     * @param competitor данные конкурента
     * @return true, если возможно найти существующую сущность
     */
    private boolean canFindExistingEntity(Competitor competitor) {
        return competitor.getCompetitorName() != null &&
                competitor.getProduct() != null;
    }

    /**
     * Копирует поля из исходных данных конкурента в целевые данные конкурента
     * @param source исходные данные конкурента
     * @param target целевые данные конкурента
     */
    private void copyCompetitorDataFields(Competitor source, Competitor target) {
        target.setCompetitorName(source.getCompetitorName());
        target.setCompetitorPrice(source.getCompetitorPrice());
        target.setCompetitorPromotionalPrice(source.getCompetitorPromotionalPrice());
        target.setCompetitorTime(source.getCompetitorTime());
        target.setCompetitorDate(source.getCompetitorDate());
        target.setCompetitorLocalDateTime(source.getCompetitorLocalDateTime());
        target.setCompetitorStockStatus(source.getCompetitorStockStatus());
        target.setCompetitorAdditionalPrice(source.getCompetitorAdditionalPrice());
        target.setCompetitorCommentary(source.getCompetitorCommentary());
        target.setCompetitorProductName(source.getCompetitorProductName());
        target.setCompetitorAdditional(source.getCompetitorAdditional());
        target.setCompetitorAdditional2(source.getCompetitorAdditional2());
        target.setCompetitorUrl(source.getCompetitorUrl());
        target.setCompetitorWebCacheUrl(source.getCompetitorWebCacheUrl());
        target.setClientId(source.getClientId());
        // Не копируем связанные сущности и ID
    }
}