package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.application.dto.TransactionInput;
import io.github.ryanoviski.hestia.domain.enums.CategoryType;
import io.github.ryanoviski.hestia.domain.enums.ProfileType;
import io.github.ryanoviski.hestia.domain.enums.TransactionStatus;
import io.github.ryanoviski.hestia.domain.enums.TransactionType;
import io.github.ryanoviski.hestia.infrastructure.database.ConnectionFactory;
import io.github.ryanoviski.hestia.infrastructure.database.DatabaseInitializer;
import io.github.ryanoviski.hestia.infrastructure.repositories.SqliteCategoryRepository;
import io.github.ryanoviski.hestia.infrastructure.repositories.SqliteProfileRepository;
import io.github.ryanoviski.hestia.infrastructure.repositories.SqliteTransactionRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
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

class ReportServiceTest {
    @TempDir Path directory;
    private ReportService reports;
    private TransactionService transactions;
    private ProfileService profiles;
    private CategoryService categories;

    @BeforeEach void setUp() throws Exception {
        Path database = directory.resolve("data/hestia.db");
        Files.createDirectories(database.getParent());
        var connections = new ConnectionFactory(database);
        new DatabaseInitializer(connections).initialize();
        var profileRepository = new SqliteProfileRepository(connections);
        var categoryRepository = new SqliteCategoryRepository(connections);
        var transactionRepository = new SqliteTransactionRepository(connections);
        Clock clock = Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneOffset.UTC);
        profiles = new ProfileService(profileRepository);
        categories = new CategoryService(categoryRepository, profileRepository);
        transactions = new TransactionService(transactionRepository, profileRepository, categoryRepository, clock);
        reports = new ReportService(transactionRepository, profileRepository, clock);
    }

    @Test void calculatesMonthlyTotalsComparisonAndProfileGrouping() {
        var ana = profiles.createProfile("Ana", ProfileType.PERSON, null);
        var family = profiles.createProfile("Família", ProfileType.SHARED, null);
        var income = categories.search(CategoryType.INCOME, null, false).getFirst();
        var housing = categories.search(CategoryType.EXPENSE, "Moradia", false).getFirst();
        var food = categories.search(CategoryType.EXPENSE, "Alimentação", false).getFirst();

        create(TransactionType.INCOME, "Salário", "5000", ana.id(), income.id(), LocalDate.of(2026, 9, 5), TransactionStatus.SETTLED);
        create(TransactionType.INCOME, "Extra", "500", ana.id(), income.id(), LocalDate.of(2026, 9, 25), TransactionStatus.PENDING);
        create(TransactionType.EXPENSE, "Aluguel", "1200", family.id(), housing.id(), LocalDate.of(2026, 9, 10), TransactionStatus.SETTLED);
        create(TransactionType.EXPENSE, "Mercado", "300", ana.id(), food.id(), LocalDate.of(2026, 9, 24), TransactionStatus.PENDING);
        create(TransactionType.INCOME, "Agosto", "4000", ana.id(), income.id(), LocalDate.of(2026, 8, 5), TransactionStatus.SETTLED);
        create(TransactionType.EXPENSE, "Agosto casa", "1000", family.id(), housing.id(), LocalDate.of(2026, 8, 10), TransactionStatus.SETTLED);

        var report = reports.monthly(YearMonth.of(2026, 9));
        assertThat(report.totalIncome()).isEqualByComparingTo("5500");
        assertThat(report.totalExpenses()).isEqualByComparingTo("1500");
        assertThat(report.current().projectedResult()).isEqualByComparingTo("4000");
        assertThat(report.previous().projectedResult()).isEqualByComparingTo("3000");
        assertThat(report.resultChange()).isEqualByComparingTo("1000");
        assertThat(report.expensesByProfile()).containsEntry("Família", new BigDecimal("1200.00"))
                .containsEntry("Ana", new BigDecimal("300.00"));
        assertThat(report.current().expensesByCategory()).containsKeys("Moradia", "Alimentação");
    }

    @Test void ignoresCancelledExpensesInDistributions() {
        var profile = profiles.createProfile("Ana", ProfileType.PERSON, null);
        var category = categories.search(CategoryType.EXPENSE, null, false).getFirst();
        var cancelled = create(TransactionType.EXPENSE, "Cancelada", "100", profile.id(), category.id(),
                LocalDate.of(2026, 9, 12), TransactionStatus.PENDING);
        transactions.cancel(cancelled.id());
        var report = reports.monthly(YearMonth.of(2026, 9));
        assertThat(report.totalExpenses()).isZero();
        assertThat(report.expensesByProfile()).isEmpty();
    }

    @Test void exportsStructuredPdfWithFinancialContent() throws Exception {
        var profile = profiles.createProfile("Ana", ProfileType.PERSON, null);
        var category = categories.search(CategoryType.EXPENSE, "Moradia", false).getFirst();
        create(TransactionType.EXPENSE, "Aluguel", "1200", profile.id(), category.id(),
                LocalDate.of(2026, 9, 10), TransactionStatus.SETTLED);
        Path file = new ReportPdfService().export(reports.monthly(YearMonth.of(2026, 9)), directory.resolve("relatorio"));
        assertThat(file).exists().hasExtension("pdf");
        try (var document = Loader.loadPDF(file.toFile())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Hestia", "Relatório financeiro", "Resumo financeiro",
                    "Despesas por categoria", "Moradia", "Despesas por perfil", "Ana");
        }
    }

    private io.github.ryanoviski.hestia.domain.models.Transaction create(
            TransactionType type, String description, String amount, long profileId, long categoryId,
            LocalDate date, TransactionStatus status) {
        return transactions.create(new TransactionInput(type, description, new BigDecimal(amount), profileId,
                categoryId, date, date, status, status == TransactionStatus.SETTLED ? date : null, null));
    }
}
