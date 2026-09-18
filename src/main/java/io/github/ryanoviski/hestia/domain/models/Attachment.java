package io.github.ryanoviski.hestia.domain.models;

import io.github.ryanoviski.hestia.domain.enums.AttachmentIntegrity;
import io.github.ryanoviski.hestia.domain.enums.DocumentType;
import java.time.Instant;

public record Attachment(Long id,long householdId,Long transactionId,Long profileId,
                         DocumentType documentType,String description,String originalFilename,
                         String storageKey,String mediaType,String fileExtension,long sizeBytes,
                         String sha256,Instant createdAt,Instant updatedAt,String profileName,
                         String transactionDescription,String transactionType,
                         AttachmentIntegrity integrity) {
    public boolean transactionTarget(){return transactionId!=null;}
}
