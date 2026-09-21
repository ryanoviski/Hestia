package io.github.ryanoviski.hestia.application.services;

import io.github.ryanoviski.hestia.application.dto.MonthlyReport;
import io.github.ryanoviski.hestia.domain.exceptions.ValidationException;
import io.github.ryanoviski.hestia.util.MoneyUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

public final class ReportPdfService {
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT_BR);
    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", PT_BR)
            .withZone(ZoneId.systemDefault());
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    public Path export(MonthlyReport report, Path destination) {
        if (report == null) throw new ValidationException("Não há relatório para exportar.");
        if (destination == null) throw new ValidationException("Escolha onde salvar o relatório.");
        Path file = destination.toAbsolutePath().normalize();
        if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            file = file.resolveSibling(file.getFileName() + ".pdf");
        }
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (PDDocument document = new PDDocument()) {
                Writer writer = new Writer(document);
                writer.title("Hestia", 22);
                writer.text("Relatório financeiro", 15, true);
                writer.text(capitalize(PERIOD.format(report.period())), 11, false);
                writer.gap(12);
                writer.rule();
                writer.section("Resumo financeiro");
                writer.metric("Receitas", MoneyUtils.format(report.totalIncome()));
                writer.metric("Despesas", MoneyUtils.format(report.totalExpenses()));
                writer.metric("Resultado projetado", MoneyUtils.format(report.current().projectedResult()));
                writer.metric("Resultado realizado", MoneyUtils.format(report.current().realizedResult()));
                writer.section("Situação no período");
                writer.row("Receitas recebidas", MoneyUtils.format(report.current().receivedIncome()));
                writer.row("Receitas previstas", MoneyUtils.format(report.current().expectedIncome()));
                writer.row("Despesas pagas", MoneyUtils.format(report.current().paidExpenses()));
                writer.row("Despesas pendentes", MoneyUtils.format(report.current().pendingExpenses()));
                writer.row("Despesas vencidas", MoneyUtils.format(report.current().overdueExpenses()));
                writer.section("Comparação com o mês anterior");
                writer.row("Variação das receitas", signed(report.incomeChange()));
                writer.row("Variação das despesas", signed(report.expenseChange()));
                writer.row("Variação do resultado", signed(report.resultChange()));
                writer.table("Despesas por categoria", report.current().expensesByCategory());
                writer.table("Despesas por perfil", report.expensesByProfile());
                writer.gap(12);
                writer.text("Gerado em " + GENERATED.format(report.generatedAt()), 9, false);
                writer.text("Hestia · Finanças em harmonia", 9, false);
                writer.close();
                document.save(file.toFile());
            }
            return file;
        } catch (IOException exception) {
            throw new ValidationException("Não foi possível gerar o PDF do relatório.");
        }
    }

    private static String signed(java.math.BigDecimal value) {
        return (value.signum() > 0 ? "+" : "") + MoneyUtils.format(value);
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static final class Writer implements AutoCloseable {
        private final PDDocument document;
        private PDPageContentStream stream;
        private float y;

        private Writer(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = 790;
        }

        private void ensure(float required) throws IOException {
            if (y - required < 48) newPage();
        }

        private void title(String value, float size) throws IOException {
            text(value, size, true);
        }

        private void section(String value) throws IOException {
            ensure(46);
            gap(18);
            text(value, 13, true);
            gap(4);
        }

        private void metric(String label, String value) throws IOException {
            ensure(29);
            text(label.toUpperCase(PT_BR), 8, true);
            text(value, 16, true);
            gap(6);
        }

        private void row(String label, String value) throws IOException {
            ensure(20);
            textAt(label, 54, y, 10, false);
            textAt(value, 390, y, 10, true);
            y -= 19;
        }

        private void table(String title, Map<String, java.math.BigDecimal> values) throws IOException {
            section(title);
            if (values.isEmpty()) {
                text("Nenhum dado disponível neste período.", 10, false);
                return;
            }
            for (var entry : values.entrySet()) row(entry.getKey(), MoneyUtils.format(entry.getValue()));
        }

        private void rule() throws IOException {
            stream.setStrokingColor(210 / 255f, 220 / 255f, 217 / 255f);
            stream.moveTo(54, y);
            stream.lineTo(541, y);
            stream.stroke();
            y -= 2;
        }

        private void gap(float amount) {
            y -= amount;
        }

        private void text(String value, float size, boolean bold) throws IOException {
            ensure(size + 8);
            textAt(value, 54, y, size, bold);
            y -= size + 5;
        }

        private void textAt(String value, float x, float atY, float size, boolean bold) throws IOException {
            stream.beginText();
            stream.setFont(bold ? BOLD : REGULAR, size);
            stream.setNonStrokingColor(35 / 255f, 52 / 255f, 48 / 255f);
            stream.newLineAtOffset(x, atY);
            stream.showText(value == null ? "" : value);
            stream.endText();
        }

        @Override public void close() throws IOException {
            if (stream != null) stream.close();
        }
    }
}
