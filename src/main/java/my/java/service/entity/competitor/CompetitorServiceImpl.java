// src/main/java/my/java/service/competitor/CompetitorDataServiceImpl.java
package my.java.service.entity.competitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.Competitor;
import my.java.repository.CompetitorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompetitorServiceImpl implements CompetitorService {

    private final CompetitorRepository competitorRepository;

    @Override
    @Transactional
    public Competitor saveCompetitor(Competitor competitor) {
        log.debug("Сохранение данных конкурента: {}", competitor.getCompetitorName());
        return competitorRepository.save(competitor);
    }

    @Override
    @Transactional
    public int saveCompetitorList(List<Competitor> competitorList) {
        if (competitorList == null || competitorList.isEmpty()) {
            return 0;
        }

        log.debug("Сохранение {} записей данных конкурентов", competitorList.size());
        List<Competitor> savedCompetitorData = competitorRepository.saveAll(competitorList);
        return savedCompetitorData.size();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Competitor> findById(Long id) {
        log.debug("Поиск данных конкурента по ID: {}", id);
        return competitorRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competitor> findByProductId(Long productId) {
        log.debug("Поиск данных конкурентов по productId: {}", productId);
        return competitorRepository.findByProductId(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competitor> findByClientId(Long clientId) {
        log.debug("Поиск данных конкурентов по clientId: {}", clientId);
        return competitorRepository.findByClientId(clientId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competitor> findByDateAfterAndClientId(LocalDateTime date, Long clientId) {
        log.debug("Поиск данных конкурентов по дате после {} и clientId: {}", date, clientId);
        return competitorRepository.findByCompetitorLocalDateTimeAfterAndClientId(date, clientId);
    }

    @Override
    @Transactional
    public void deleteCompetitor(Long id) {
        log.debug("Удаление данных конкурента по ID: {}", id);
        competitorRepository.deleteById(id);
    }

    @Override
    @Transactional
    public int deleteByProductId(Long productId) {
        log.debug("Удаление данных конкурентов по productId: {}", productId);

        // Сначала подсчитываем количество записей для удаления
        List<Competitor> recordsToDelete = competitorRepository.findByProductId(productId);
        if (recordsToDelete.isEmpty()) {
            return 0;
        }

        // Удаляем записи
        competitorRepository.deleteByProductId(productId);
        return recordsToDelete.size();
    }

    @Override
    @Transactional
    public int deleteByClientId(Long clientId) {
        log.debug("Удаление данных конкурентов по clientId: {}", clientId);

        // Сначала подсчитываем количество записей для удаления
        List<Competitor> recordsToDelete = competitorRepository.findByClientId(clientId);
        if (recordsToDelete.isEmpty()) {
            return 0;
        }

        // Удаляем записи
        competitorRepository.deleteByClientId(clientId);
        return recordsToDelete.size();
    }

    @Override
    @Transactional
    public Competitor upsertCompetitor(Competitor competitor) {
        log.debug("Обновление/создание данных конкурента: {}", competitor.getCompetitorName());

        // Проверяем существование данных конкурента по названию и продукту
        if (competitor.getCompetitorName() != null && competitor.getProduct() != null) {
            Optional<Competitor> existingData = competitorRepository.findByCompetitorNameAndProductId(
                    competitor.getCompetitorName(), competitor.getProduct().getId());

            if (existingData.isPresent()) {
                // Обновляем существующие данные
                Competitor existing = existingData.get();
                // Обновляем все поля, кроме ID и продукта
                copyCompetitorDataFields(competitor, existing);
                return competitorRepository.save(existing);
            }
        }

        // Создаем новые данные конкурента
        return competitorRepository.save(competitor);
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