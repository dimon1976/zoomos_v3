// src/main/java/my/java/service/entity/competitor/CompetitorServiceImpl.java
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
    @Transactional(readOnly = true)
    public Optional<Competitor> findByCompetitorNameAndProductId(String competitorName, Long productId) {
        return competitorRepository.findByCompetitorNameAndProductId(competitorName, productId);
    }

    @Override
    @Transactional
    public Competitor saveCompetitor(Competitor competitor) {
        return competitorRepository.save(competitor);
    }

    @Override
    @Transactional
    public int saveCompetitorList(List<Competitor> competitorList) {
        if (competitorList == null || competitorList.isEmpty()) return 0;
        return competitorRepository.saveAll(competitorList).size();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Competitor> findById(Long id) {
        return competitorRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competitor> findByProductId(Long productId) {
        return competitorRepository.findByProductId(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competitor> findByClientId(Long clientId) {
        return competitorRepository.findByClientId(clientId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competitor> findByDateAfterAndClientId(LocalDateTime date, Long clientId) {
        return competitorRepository.findByCompetitorLocalDateTimeAfterAndClientId(date, clientId);
    }

    @Override
    @Transactional
    public void deleteCompetitor(Long id) {
        competitorRepository.deleteById(id);
    }

    @Override
    @Transactional
    public int deleteByProductId(Long productId) {
        List<Competitor> recordsToDelete = competitorRepository.findByProductId(productId);
        if (recordsToDelete.isEmpty()) return 0;

        competitorRepository.deleteByProductId(productId);
        return recordsToDelete.size();
    }

    @Override
    @Transactional
    public int deleteByClientId(Long clientId) {
        List<Competitor> recordsToDelete = competitorRepository.findByClientId(clientId);
        if (recordsToDelete.isEmpty()) return 0;

        competitorRepository.deleteByClientId(clientId);
        return recordsToDelete.size();
    }

    @Override
    @Transactional
    public Competitor upsertCompetitor(Competitor competitor) {
        if (competitor.getCompetitorName() != null && competitor.getProduct() != null) {
            Optional<Competitor> existingData = competitorRepository.findByCompetitorNameAndProductId(
                    competitor.getCompetitorName(), competitor.getProduct().getId());

            if (existingData.isPresent()) {
                Competitor existing = existingData.get();
                copyCompetitorDataFields(competitor, existing);
                return competitorRepository.save(existing);
            }
        }
        return competitorRepository.save(competitor);
    }

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
    }
}