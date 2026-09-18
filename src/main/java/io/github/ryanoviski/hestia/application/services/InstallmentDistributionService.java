package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import java.util.ArrayList;
import java.util.List;

public final class InstallmentDistributionService {
    public List<Long> distribute(long totalCents, int count) {
        if (totalCents <= 0) throw new ValidationException("O valor total deve ser maior que zero.");
        if (count < 1 || count > 120) throw new ValidationException("A quantidade deve estar entre 1 e 120 parcelas.");
        if (totalCents < count) throw new ValidationException("Cada parcela deve ter ao menos um centavo.");
        long base = totalCents / count, remainder = totalCents % count;
        List<Long> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(base + (i < remainder ? 1 : 0));
        return List.copyOf(result);
    }
}
