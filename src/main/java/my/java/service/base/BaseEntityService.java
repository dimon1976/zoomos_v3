// src/main/java/my/java/service/base/BaseEntityService.java
package my.java.service.base;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Базовый класс для сервисов, работающих с сущностями
 *
 * @param <T> тип сущности
 * @param <ID> тип идентификатора сущности
 * @param <R> тип репозитория
 */
@Slf4j
public abstract class BaseEntityService<T, ID, R extends JpaRepository<T, ID>> {

    protected final R repository;

    protected BaseEntityService(R repository) {
        this.repository = repository;
    }

    /**
     * Сохраняет сущность
     *
     * @param entity сущность для сохранения
     * @return сохраненная сущность
     */
    @Transactional
    public T save(T entity) {
        logSave(entity);
        return repository.save(entity);
    }

    /**
     * Сохраняет список сущностей
     *
     * @param entities список сущностей для сохранения
     * @return количество сохраненных сущностей
     */
    @Transactional
    public int saveAll(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }

        logSaveAll(entities);
        return repository.saveAll(entities).size();
    }

    /**
     * Находит сущность по ID
     *
     * @param id идентификатор сущности
     * @return опциональная сущность
     */
    @Transactional(readOnly = true)
    public Optional<T> findById(ID id) {
        logFindById(id);
        return repository.findById(id);
    }

    /**
     * Удаляет сущность по ID
     *
     * @param id идентификатор сущности
     */
    @Transactional
    public void deleteById(ID id) {
        logDeleteById(id);
        repository.deleteById(id);
    }

    /**
     * Логирует сохранение сущности
     *
     * @param entity сохраняемая сущность
     */
    protected abstract void logSave(T entity);

    /**
     * Логирует сохранение списка сущностей
     *
     * @param entities список сохраняемых сущностей
     */
    protected void logSaveAll(List<T> entities) {
        log.debug("Сохранение {} сущностей", entities.size());
    }

    /**
     * Логирует поиск сущности по ID
     *
     * @param id идентификатор сущности
     */
    protected void logFindById(ID id) {
        log.debug("Поиск сущности по ID: {}", id);
    }

    /**
     * Логирует удаление сущности по ID
     *
     * @param id идентификатор сущности
     */
    protected void logDeleteById(ID id) {
        log.debug("Удаление сущности по ID: {}", id);
    }
}