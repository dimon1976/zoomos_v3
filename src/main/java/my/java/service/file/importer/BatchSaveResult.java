package my.java.service.file.importer;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Результат пакетного сохранения
 */
@Getter
public class BatchSaveResult {
    private int saved = 0;
    private int updated = 0;
    private int skipped = 0;
    private int failed = 0;
    private final List<String> errors = new ArrayList<>();

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public void addSkipped(int count) {
        this.skipped += count;
    }

    public void setSaved(int saved) {
        this.saved = saved;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }

    public void incrementSkipped() {
        this.skipped++;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public void addError(String error) {
        errors.add(error);
    }

    public int getTotal() {
        return saved + updated + skipped + failed;
    }
}
