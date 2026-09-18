package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.LocalDate;
import java.time.YearMonth;
import static org.assertj.core.api.Assertions.*;

class MonthlyCommitmentRulesTest {
    @ParameterizedTest @CsvSource({
            "2027-01-31,2027-02,2027-02-28", "2028-01-31,2028-02,2028-02-29",
            "2027-01-31,2027-04,2027-04-30", "2027-01-30,2027-03,2027-03-30"})
    void preservesOriginalDayAndUsesLastAvailableDay(String first,String month,String expected){
        assertThat(MonthlyDueDateCalculator.forMonth(LocalDate.parse(first),YearMonth.parse(month)))
                .isEqualTo(LocalDate.parse(expected));
    }
    @Test void distributesRemainderToFirstInstallments(){
        assertThat(new InstallmentDistributionService().distribute(10_000,3)).containsExactly(3334L,3333L,3333L);
    }
    @Test void distributionAlwaysMatchesTotal(){
        var values=new InstallmentDistributionService().distribute(123_457,17);
        assertThat(values).hasSize(17);assertThat(values.stream().mapToLong(Long::longValue).sum()).isEqualTo(123_457);
    }
    @Test void rejectsInvalidCountsAndSubCentInstallments(){
        var service=new InstallmentDistributionService();
        assertThatThrownBy(()->service.distribute(100,0)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(()->service.distribute(100,121)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(()->service.distribute(2,3)).isInstanceOf(ValidationException.class);
    }
}
