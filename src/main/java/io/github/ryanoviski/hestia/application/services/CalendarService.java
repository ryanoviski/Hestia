package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.application.dto.CalendarMonth;
import io.github.ryanoviski.hestia.application.dto.TransactionFilter;
import io.github.ryanoviski.hestia.application.repositories.ProfileRepository;
import io.github.ryanoviski.hestia.application.repositories.TransactionRepository;
import io.github.ryanoviski.hestia.domain.models.Transaction;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class CalendarService {
    private final TransactionRepository transactions;
    private final ProfileRepository profiles;
    private final RecurringExpenseService recurring;
    private final Clock clock;
    public CalendarService(TransactionRepository transactions, ProfileRepository profiles,
                           RecurringExpenseService recurring, Clock clock){this.transactions=transactions;this.profiles=profiles;this.recurring=recurring;this.clock=clock;}
    public CalendarMonth load(YearMonth month,TransactionFilter filter){
        recurring.ensureGeneratedThrough(month);
        long household=profiles.findDefaultHouseholdId();
        TransactionFilter scoped=new TransactionFilter(filter.search(),month,filter.type(),filter.status(),
                filter.profileId(),filter.categoryId(),filter.overdueOnly(),true,filter.origin());
        List<Transaction> items=transactions.searchCalendar(household,scoped,clock);
        Map<LocalDate,List<Transaction>> days=items.stream().collect(Collectors.groupingBy(
                t->t.dueDate()==null?t.referenceDate():t.dueDate(),LinkedHashMap::new,Collectors.toList()));
        return new CalendarMonth(month,days,transactions.summarize(household,month,clock));
    }
}
