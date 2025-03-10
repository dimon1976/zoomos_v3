// src/main/java/my/java/service/competitor/CompetitorDataServiceImpl.java
package my.java.service.competitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.CompetitorData;
import my.java.repository.CompetitorDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompetitorDataServiceImpl implements CompetitorDataService {

    private final CompetitorDataRepository competitorDataRepository;

    @Override
    @Transactional
    public CompetitorData saveCompetitorData(CompetitorData competitorData) {
        log.debug("Сохранение данных конкурента: {}", competitorData.getCompetitorName());
        return competitorDataRepository.save(competitorData);
    }

    @Override
    @Transactional
    public int saveCompetitorDataList(List<CompetitorData> competitorDataList) {
        if (competitorDataList == null || competitorDataList.isEmpty()) {
            return 0;
        }

        log.debug("Сохранение {} записей данных конкурентов", competitorDataList.size());
        List<CompetitorData> savedCompetitorData = competitorDataRepository.saveAll(competitorDataList);
        return savedCompetitorData.size();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CompetitorData> findById(Long id) {
        log.debug("Поиск данных конкурента по ID: {}", id);
        return competitorDataRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompetitorData> findByProductId(Long productId) {
        log.debug("Поиск данных конкурентов по productId: {}", productId);
        return competitorDataRepository.findByProductId(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompetitorData> findByClientId(Long clientId) {
        log.debug("Поиск данных конкурентов по clientId: {}", clientId);
        return competitorDataRepository.findByClientId(clientId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompetitorData> findByDateAfterAndClientId(LocalDateTime date, Long clientId) {
        log.debug("Поиск данных конкурентов по дате после {} и clientId: {}", date, clientId);
        return competitorDataRepository.findByCompetitorLocalDateTimeAfterAndClientId(date, clientId);
    }

    @Override
    @Transactional
    public void deleteCompetitorData(Long id) {
        log.debug("Удаление данных конкурента по ID: {}", id);
        competitorDataRepository.deleteById(id);
    }

    @Override
    @Transactional
    public int deleteByProductId(Long productId) {
        log.debug("Удаление данных конкурентов по productId: {}", productId);

        // Сначала подсчитываем количество записей для удаления
        List<CompetitorData> recordsToDelete = competitorDataRepository.findByProductId(productId);
        if (recordsToDelete.isEmpty()) {
            return 0;
        }

        // Удаляем записи
        competitorDataRepository.deleteByProductId(productId);
        return recordsToDelete.size();
    }

    @Override
    @Transactional
    public int deleteByClientId(Long clientId) {
        log.debug("Удаление данных конкурентов по clientId: {}", clientId);

        // Сначала подсчитываем количество записей для удаления
        List<CompetitorData> recordsToDelete = competitorDataRepository.findByClientId(clientId);
        if (recordsToDelete.isEmpty()) {
            return 0;
        }

        // Удаляем записи
        competitorDataRepository.deleteByClientId(clientId);
        return recordsToDelete.size();
    }

    @Override
    @Transactional
    public CompetitorData upsertCompetitorData(CompetitorData competitorData) {
        log.debug("Обновление/создание данных конкурента: {}", competitorData.getCompetitorName());

        // Проверяем существование данных конкурента по названию и продукту
        if (competitorData.getCompetitorName() != null && competitorData.getProduct() != null) {
            Optional<CompetitorData> existingData = competitorDataRepository.findByCompetitorNameAndProductId(
                    competitorData.getCompetitorName(), competitorData.getProduct().getId());

            if (existingData.isPresent()) {
                // Обновляем существующие данные
                CompetitorData existing = existingData.get();
                // Обновляем все поля, кроме ID и продукта
                copyCompetitorDataFields(competitorData, existing);
                return competitorDataRepository.save(existing);
            }
        }

        // Создаем новые данные конкурента
        return competitorDataRepository.save(competitorData);
    }

    /**
     * Копирует поля из исходных данных конкурента в целевые данные конкурента
     * @param source исходные данные конкурента
     * @param target целевые данные конкурента
     */
    private void copyCompetitorDataFields(CompetitorData source, CompetitorData target) {
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