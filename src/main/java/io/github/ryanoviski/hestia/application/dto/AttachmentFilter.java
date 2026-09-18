package io.github.ryanoviski.hestia.application.dto;

import io.github.ryanoviski.hestia.domain.enums.AttachmentIntegrity;
import io.github.ryanoviski.hestia.domain.enums.DocumentType;
import io.github.ryanoviski.hestia.domain.enums.TransactionType;
import java.time.LocalDate;

public record AttachmentFilter(String name,Long profileId,DocumentType documentType,
                               TransactionType transactionType,LocalDate from,LocalDate to,
                               Boolean transactionTarget,AttachmentIntegrity integrity){
    public static AttachmentFilter empty(){return new AttachmentFilter(null,null,null,null,null,null,null,null);}
}
