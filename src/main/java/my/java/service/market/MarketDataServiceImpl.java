package my.java.service.market;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.java.model.entity.MarketData;
import my.java.repository.MarketDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Реализация универсального сервиса для управления рыночными данными.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MarketDataServiceImpl implements MarketDataService {

    private final MarketDataRepository marketDataRepository;

    @Override
    @Transactional
    public MarketData saveMarketData(MarketData marketData) {
        String dataType = isRegionData(marketData) ? "региона" : "конкурента";
        String identifier = isRegionData(marketData) ? marketData.getRegion() : marketData.getCompetitorName();

        log.debug("Сохранение данных {}: {}", dataType, identifier);
        return marketDataRepository.save(marketData);
    }

    @Override
    @Transactional
    public int saveMarketDataList(List<MarketData> marketDataList) {
        if (marketDataList == null || marketDataList.isEmpty()) {
            return 0;
        }

        log.debug("Сохранение {} записей рыночных данных", marketDataList.size());
        List<MarketData> savedMarketData = marketDataRepository.saveAll(marketDataList);
        return savedMarketData.size();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MarketData> findById(Long id) {
        log.debug("Поиск рыночных данных по ID: {}", id);
        return marketDataRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MarketData> findByProductId(Long productId) {
        log.debug("Поиск рыночных данных по productId: {}", productId);
        return marketDataRepository.findByProductId(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MarketData> findByClientId(Long clientId) {
        log.debug("Поиск рыночных данных по clientId: {}", clientId);
        return marketDataRepository.findByClientId(clientId);
    }

//    @Override
//    @Transactional(readOnly = true)
//    public List<MarketData> findByDateAfterAndClientId(LocalDateTime date, Long clientId) {
//        log.debug("Поиск рыночных данных по дате после {} и clientId: {}", date, clientId);
//        return marketDataRepository.findByCompetitorLocalDateTimeAfterAndClientId(date, clientId);
//    }

    @Override
    @Transactional(readOnly = true)
    public List<MarketData> findByRegion(String region) {
        log.debug("Поиск рыночных данных по региону: {}", region);
        return marketDataRepository.findByRegion(region);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MarketData> findByCompetitorName(String competitorName) {
        log.debug("Поиск рыночных данных по конкуренту: {}", competitorName);
        return marketDataRepository.findByCompetitorName(competitorName);
    }

    @Override
    @Transactional
    public void deleteMarketData(Long id) {
        log.debug("Удаление рыночных данных по ID: {}", id);
        marketDataRepository.deleteById(id);
    }

    @Override
    @Transactional
    public int deleteByProductId(Long productId) {
        log.debug("Удаление рыночных данных по productId: {}", productId);

        // Сначала подсчитываем количество записей для удаления
        List<MarketData> recordsToDelete = marketDataRepository.findByProductId(productId);
        if (recordsToDelete.isEmpty()) {
            return 0;
        }

        // Удаляем записи
        marketDataRepository.deleteByProductId(productId);
        return recordsToDelete.size();
    }

    @Override
    @Transactional
    public int deleteByClientId(Long clientId) {
        log.debug("Удаление рыночных данных по clientId: {}", clientId);

        // Сначала подсчитываем количество записей для удаления
        List<MarketData> recordsToDelete = marketDataRepository.findByClientId(clientId);
        if (recordsToDelete.isEmpty()) {
            return 0;
        }

        // Удаляем записи
        marketDataRepository.deleteByClientId(clientId);
        return recordsToDelete.size();
    }

    @Override
    @Transactional
    public MarketData upsertMarketData(MarketData marketData) {
        // Определяем тип данных (регион или конкурент)
        boolean isRegion = isRegionData(marketData);
        String dataType = isRegion ? "региона" : "конкурента";
        String identifier = isRegion ? marketData.getRegion() : marketData.getCompetitorName();

        log.debug("Обновление/создание данных {}: {}", dataType, identifier);

        Optional<MarketData> existingData = Optional.empty();

        // Проверяем существование данных по соответствующему критерию
        if (marketData.getProduct() != null) {
            if (isRegion && marketData.getRegion() != null) {
                existingData = marketDataRepository.findByRegionAndProductId(
                        marketData.getRegion(), marketData.getProduct().getId());
            } else if (!isRegion && marketData.getCompetitorName() != null) {
                existingData = marketDataRepository.findByCompetitorNameAndProductId(
                        marketData.getCompetitorName(), marketData.getProduct().getId());
            }
        }

        if (existingData.isPresent()) {
            // Обновляем существующие данные
            MarketData existing = existingData.get();
            // Копируем все поля в зависимости от типа данных
            copyMarketDataFields(marketData, existing);
            return marketDataRepository.save(existing);
        }

        // Создаем новые данные
        return marketDataRepository.save(marketData);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findAllRegions() {
        return marketDataRepository.findAllRegions();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findAllCompetitors() {
        return marketDataRepository.findAllCompetitors();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findRegionsByProductId(Long productId) {
        return marketDataRepository.findRegionsByProductId(productId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findCompetitorsByProductId(Long productId) {
        return marketDataRepository.findCompetitorsByProductId(productId);
    }

    /**
     * Определяет, являются ли данные региональными (true) или данными конкурента (false)
     */
    private boolean isRegionData(MarketData marketData) {
        return marketData.getRegion() != null && !marketData.getRegion().isEmpty();
    }

    /**
     * Копирует поля из исходных данных в целевые данные
     */
    private void copyMarketDataFields(MarketData source, MarketData target) {
        // Общие поля
        target.setClientId(source.getClientId());

        // Поля региона
        if (source.getRegion() != null) {
            target.setRegion(source.getRegion());
            target.setRegionAddress(source.getRegionAddress());
        }

        // Поля конкурента
        if (source.getCompetitorName() != null) {
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
        }

        // Не копируем связанные сущности и ID
    }
}