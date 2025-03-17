package my.java.service.file;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class FileOperationService {
    public List<FileOperationMetadata> getRecentOperationsForClient(Long id, int i) {
        return null;
    }

    public Page<FileOperationMetadata> getClientOperations(Long id, LocalDate startDate, LocalDate endDate, String type, String status, Pageable pageable) {
        return null;
    }

    public Long initiateExport(Long id, String dataType, LocalDate startDate, LocalDate endDate, String fileFormat, boolean includeHeaders, boolean zipFiles) {
        return null;
    }
}
