package io.github.ryanoviski.hestia.application.repositories;

import io.github.ryanoviski.hestia.application.dto.AttachmentFilter;
import io.github.ryanoviski.hestia.domain.models.Attachment;
import java.util.List;import java.util.Optional;

public interface AttachmentRepository {
    Attachment insert(Attachment attachment);
    Optional<Attachment> findById(long householdId,long id);
    List<Attachment> search(long householdId,AttachmentFilter filter);
    void delete(long householdId,long id);
    List<String> storageKeys(long householdId);
}
