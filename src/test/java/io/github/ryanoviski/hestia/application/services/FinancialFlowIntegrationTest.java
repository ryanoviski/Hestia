package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.application.dto.TransactionFilter;
import io.github.ryanoviski.hestia.application.dto.TransactionInput;
import io.github.ryanoviski.hestia.domain.enums.CategoryType;
import io.github.ryanoviski.hestia.domain.enums.ProfileType;
import io.github.ryanoviski.hestia.domain.enums.TransactionStatus;
import io.github.ryanoviski.hestia.domain.enums.TransactionType;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.domain.models.Category;
import io.github.ryanoviski.hestia.domain.models.Profile;
import io.github.ryanoviski.hestia.infrastructure.database.ConnectionFactory;
import io.github.ryanoviski.hestia.infrastructure.database.DatabaseInitializer;
import io.github.ryanoviski.hestia.infrastructure.repositories.SqliteCategoryRepository;
import io.github.ryanoviski.hestia.infrastructure.repositories.SqliteProfileRepository;
import io.github.ryanoviski.hestia.infrastructure.repositories.SqliteTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinancialFlowIntegrationTest {
    @TempDir Path directory;
    private CategoryService categories;
    private ProfileService profiles;
    private TransactionService transactions;
    private DashboardService dashboard;
    private Profile activeProfile;
    private Category incomeCategory;
    private Category expenseCategory;
    private Clock clock;

    @BeforeEach void setUp() throws Exception {
        Path database=directory.resolve("isolated").resolve("hestia.db"); Files.createDirectories(database.getParent());
        ConnectionFactory factory=new ConnectionFactory(database); new DatabaseInitializer(factory).initialize();
        var profileRepository=new SqliteProfileRepository(factory); var categoryRepository=new SqliteCategoryRepository(factory); var transactionRepository=new SqliteTransactionRepository(factory);
        clock=Clock.fixed(Instant.parse("2026-09-18T12:00:00Z"), ZoneOffset.UTC);
        profiles=new ProfileService(profileRepository); categories=new CategoryService(categoryRepository,profileRepository);
        transactions=new TransactionService(transactionRepository,profileRepository,categoryRepository,clock);
        dashboard=new DashboardService(transactionRepository,profileRepository,clock);
        activeProfile=profiles.createProfile("Ana", ProfileType.PERSON,"#123456");
        incomeCategory=categories.search(CategoryType.INCOME,null,false).getFirst();
        expenseCategory=categories.search(CategoryType.EXPENSE,null,false).getFirst();
    }

    @Test void createsEditsDeactivatesAndReactivatesCustomCategory() {
        Category created=categories.create("  Animais   de estimação ",CategoryType.EXPENSE,"#abcdef");
        assertThat(created.name()).isEqualTo("Animais de estimação"); assertThat(created.color()).isEqualTo("#ABCDEF");
        Category updated=categories.update(created.id(),"Pets",CategoryType.EXPENSE,"#112233");
        assertThat(updated.name()).isEqualTo("Pets");
        assertThatThrownBy(()->categories.create(" pets ",CategoryType.EXPENSE,null)).isInstanceOf(ValidationException.class).hasMessageContaining("Já existe");
        categories.setActive(created.id(),false);
        assertThat(categories.search(CategoryType.EXPENSE,"Pets",false)).isEmpty();
        assertThat(categories.search(CategoryType.EXPENSE,"Pets",true)).singleElement().satisfies(category->assertThat(category.active()).isFalse());
        categories.setActive(created.id(),true);
        assertThat(categories.search(CategoryType.EXPENSE,"Pets",false)).hasSize(1);
    }

    @Test void createsEditsSettlesReopensAndCancelsTransactions() {
        var income=transactions.create(input(TransactionType.INCOME,incomeCategory,"Salário",new BigDecimal("5000.00"),TransactionStatus.PENDING,LocalDate.of(2026,9,20),null));
        assertThat(income.amountCents()).isEqualTo(500000);
        transactions.settle(income.id(),LocalDate.of(2026,9,20));
        assertThat(transactions.find(income.id()).status()).isEqualTo(TransactionStatus.SETTLED);
        assertThat(transactions.find(income.id()).settlementDate()).isEqualTo(LocalDate.of(2026,9,20));
        transactions.reopen(income.id()); assertThat(transactions.find(income.id()).settlementDate()).isNull();
        var edited=transactions.update(income.id(),input(TransactionType.INCOME,incomeCategory,"Salário ajustado",new BigDecimal("5100.00"),TransactionStatus.PENDING,LocalDate.of(2026,9,21),null));
        assertThat(edited.description()).isEqualTo("Salário ajustado");
        transactions.cancel(income.id()); assertThat(transactions.find(income.id()).status()).isEqualTo(TransactionStatus.CANCELLED);
        assertThat(transactions.search(new TransactionFilter(null,YearMonth.of(2026,9),null,TransactionStatus.CANCELLED,null,null,false,false))).hasSize(1);
    }

    @Test void rejectsInvalidTransactionRulesAndDetectsOverdueExpense() {
        assertThatThrownBy(()->transactions.create(input(TransactionType.EXPENSE,incomeCategory,"Inválida",BigDecimal.TEN,TransactionStatus.PENDING,LocalDate.of(2026,9,1),null))).isInstanceOf(ValidationException.class).hasMessageContaining("compatível");
        assertThatThrownBy(()->transactions.create(input(TransactionType.EXPENSE,expenseCategory,"",BigDecimal.TEN,TransactionStatus.PENDING,null,null))).isInstanceOf(ValidationException.class).hasMessageContaining("descrição");
        assertThatThrownBy(()->transactions.create(input(TransactionType.EXPENSE,expenseCategory,"Zero",BigDecimal.ZERO,TransactionStatus.PENDING,null,null))).isInstanceOf(ValidationException.class).hasMessageContaining("maior que zero");
        assertThatThrownBy(()->transactions.create(input(TransactionType.EXPENSE,expenseCategory,"Negativa",BigDecimal.ONE.negate(),TransactionStatus.PENDING,null,null))).isInstanceOf(ValidationException.class);
        var overdue=transactions.create(input(TransactionType.EXPENSE,expenseCategory,"Conta vencida",new BigDecimal("100"),TransactionStatus.PENDING,LocalDate.of(2026,9,17),null));
        assertThat(overdue.isOverdue(clock)).isTrue();
        assertThat(transactions.search(new TransactionFilter(null,null,TransactionType.EXPENSE,null,null,null,true,true))).extracting("id").containsExactly(overdue.id());
    }

    @Test void rejectsInactiveProfileAndCategoryButPreservesHistory() {
        Category custom=categories.create("Casa",CategoryType.EXPENSE,null);
        var saved=transactions.create(input(TransactionType.EXPENSE,custom,"Conserto",new BigDecimal("80"),TransactionStatus.PENDING,null,null));
        categories.setActive(custom.id(),false);
        assertThat(transactions.find(saved.id()).categoryName()).isEqualTo("Casa");
        assertThatThrownBy(()->transactions.create(input(TransactionType.EXPENSE,custom,"Outro",BigDecimal.TEN,TransactionStatus.PENDING,null,null))).isInstanceOf(ValidationException.class).hasMessageContaining("inativa");
        profiles.deactivateProfile(activeProfile.id());
        assertThat(transactions.find(saved.id()).profileName()).isEqualTo("Ana");
        assertThatThrownBy(()->transactions.create(input(TransactionType.EXPENSE,expenseCategory,"Outro",BigDecimal.TEN,TransactionStatus.PENDING,null,null))).isInstanceOf(ValidationException.class).hasMessageContaining("inativo");
    }

    @Test void calculatesMonthlyDashboardAndExcludesCancelledTransactions() {
        create(TransactionType.INCOME,incomeCategory,"Recebida","1000",TransactionStatus.SETTLED,LocalDate.of(2026,9,5),LocalDate.of(2026,9,5));
        create(TransactionType.INCOME,incomeCategory,"Prevista","200",TransactionStatus.PENDING,LocalDate.of(2026,9,25),null);
        create(TransactionType.EXPENSE,expenseCategory,"Paga","300",TransactionStatus.SETTLED,LocalDate.of(2026,9,10),LocalDate.of(2026,9,10));
        create(TransactionType.EXPENSE,expenseCategory,"Vencida","100",TransactionStatus.PENDING,LocalDate.of(2026,9,15),null);
        create(TransactionType.EXPENSE,expenseCategory,"Próxima","80",TransactionStatus.PENDING,LocalDate.of(2026,9,22),null);
        var cancelled=create(TransactionType.EXPENSE,expenseCategory,"Cancelada","50",TransactionStatus.PENDING,LocalDate.of(2026,9,20),null); transactions.cancel(cancelled.id());
        transactions.create(new TransactionInput(TransactionType.INCOME,"Outro mês",new BigDecimal("999"),
                activeProfile.id(),incomeCategory.id(),LocalDate.of(2026,8,1),LocalDate.of(2026,8,1),
                TransactionStatus.SETTLED,LocalDate.of(2026,8,1),null));

        var summary=dashboard.summary(YearMonth.of(2026,9));
        assertThat(summary.receivedIncome()).isEqualByComparingTo("1000"); assertThat(summary.expectedIncome()).isEqualByComparingTo("200");
        assertThat(summary.paidExpenses()).isEqualByComparingTo("300"); assertThat(summary.pendingExpenses()).isEqualByComparingTo("180");
        assertThat(summary.overdueExpenses()).isEqualByComparingTo("100"); assertThat(summary.realizedResult()).isEqualByComparingTo("700");
        assertThat(summary.projectedResult()).isEqualByComparingTo("720");
        assertThat(summary.expensesByCategory().values()).singleElement()
                .satisfies(value -> assertThat(value).isEqualByComparingTo("480"));
        assertThat(summary.upcomingDue()).extracting("description").containsExactly("Próxima");
    }

    private io.github.ryanoviski.hestia.domain.models.Transaction create(TransactionType type,Category category,String description,String amount,TransactionStatus status,LocalDate due,LocalDate settled){return transactions.create(input(type,category,description,new BigDecimal(amount),status,due,settled));}
    private TransactionInput input(TransactionType type,Category category,String description,BigDecimal amount,TransactionStatus status,LocalDate due,LocalDate settled){return new TransactionInput(type,description,amount,activeProfile.id(),category.id(),LocalDate.of(2026,9,1),due,status,settled,null);}
}
