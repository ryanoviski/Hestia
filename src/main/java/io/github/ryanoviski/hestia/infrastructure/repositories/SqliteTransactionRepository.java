package io.github.ryanoviski.hestia.infrastructure.repositories;

import io.github.ryanoviski.hestia.application.dto.DashboardSummary;
import io.github.ryanoviski.hestia.application.dto.TransactionFilter;
import io.github.ryanoviski.hestia.application.repositories.TransactionRepository;
import io.github.ryanoviski.hestia.domain.enums.TransactionStatus;
import io.github.ryanoviski.hestia.domain.enums.TransactionType;
import io.github.ryanoviski.hestia.domain.models.Transaction;
import io.github.ryanoviski.hestia.infrastructure.database.ConnectionFactory;
import io.github.ryanoviski.hestia.infrastructure.database.DatabaseException;
import io.github.ryanoviski.hestia.util.MoneyUtils;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SqliteTransactionRepository implements TransactionRepository {
    private static final String SELECT = """
            SELECT t.*, p.name profile_name, c.name category_name
            FROM transactions t JOIN profiles p ON p.id=t.profile_id JOIN categories c ON c.id=t.category_id
            """;
    private final ConnectionFactory connections;

    public SqliteTransactionRepository(ConnectionFactory connections) { this.connections = connections; }

    @Override public Transaction insert(Transaction transaction) {
        String sql = """
                INSERT INTO transactions(household_id,profile_id,category_id,transaction_type,description,
                amount_cents,reference_date,due_date,settlement_date,status,notes,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (var connection = connections.openConnection();
             var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindAll(statement, transaction, 1);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No transaction identifier returned");
                return withId(transaction, keys.getLong(1));
            }
        } catch (SQLException exception) { throw failure("insert transaction", exception); }
    }

    @Override public Transaction update(Transaction transaction) {
        String sql = """
                UPDATE transactions SET profile_id=?,category_id=?,transaction_type=?,description=?,amount_cents=?,
                reference_date=?,due_date=?,settlement_date=?,status=?,notes=?,updated_at=?
                WHERE id=? AND household_id=?
                """;
        try (var connection = connections.openConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, transaction.profileId()); statement.setLong(2, transaction.categoryId());
            statement.setString(3, transaction.type().name()); statement.setString(4, transaction.description());
            statement.setLong(5, transaction.amountCents()); statement.setString(6, transaction.referenceDate().toString());
            setDate(statement, 7, transaction.dueDate()); setDate(statement, 8, transaction.settlementDate());
            statement.setString(9, transaction.status().name()); statement.setString(10, transaction.notes());
            statement.setString(11, transaction.updatedAt().toString()); statement.setLong(12, transaction.id());
            statement.setLong(13, transaction.householdId());
            if (statement.executeUpdate() != 1) throw new SQLException("Transaction was not updated");
            return transaction;
        } catch (SQLException exception) { throw failure("update transaction", exception); }
    }

    @Override public Optional<Transaction> findById(long id, long householdId) {
        try (var connection = connections.openConnection();
             var statement = connection.prepareStatement(SELECT + " WHERE t.id=? AND t.household_id=?")) {
            statement.setLong(1, id); statement.setLong(2, householdId);
            try (var result = statement.executeQuery()) { return result.next() ? Optional.of(map(result)) : Optional.empty(); }
        } catch (SQLException exception) { throw failure("find transaction", exception); }
    }

    @Override public List<Transaction> search(long householdId, TransactionFilter filter, Clock clock) {
        StringBuilder sql = new StringBuilder(SELECT + " WHERE t.household_id=?");
        List<Object> values = new ArrayList<>(); values.add(householdId);
        if (filter.search() != null && !filter.search().isBlank()) { sql.append(" AND lower(t.description) LIKE lower(?)"); values.add("%" + filter.search().trim() + "%"); }
        if (filter.month() != null) { sql.append(" AND t.reference_date>=? AND t.reference_date<?"); values.add(filter.month().atDay(1).toString()); values.add(filter.month().plusMonths(1).atDay(1).toString()); }
        if (filter.type() != null) { sql.append(" AND t.transaction_type=?"); values.add(filter.type().name()); }
        if (filter.status() != null) { sql.append(" AND t.status=?"); values.add(filter.status().name()); }
        if (filter.profileId() != null) { sql.append(" AND t.profile_id=?"); values.add(filter.profileId()); }
        if (filter.categoryId() != null) { sql.append(" AND t.category_id=?"); values.add(filter.categoryId()); }
        if (filter.overdueOnly()) { sql.append(" AND t.transaction_type='EXPENSE' AND t.status='PENDING' AND t.due_date<?"); values.add(LocalDate.now(clock).toString()); }
        sql.append(filter.dueDateAscending()
                ? " ORDER BY t.due_date IS NULL, t.due_date, t.id"
                : " ORDER BY t.reference_date DESC, t.id DESC");
        try (var connection = connections.openConnection(); var statement = connection.prepareStatement(sql.toString())) {
            bindValues(statement, values);
            try (var result = statement.executeQuery()) {
                List<Transaction> transactions = new ArrayList<>(); while (result.next()) transactions.add(map(result)); return transactions;
            }
        } catch (SQLException exception) { throw failure("search transactions", exception); }
    }

    @Override public void updateStatus(long id, long householdId, TransactionStatus status, LocalDate settlementDate) {
        try (var connection = connections.openConnection(); var statement = connection.prepareStatement("""
                UPDATE transactions SET status=?,settlement_date=?,updated_at=? WHERE id=? AND household_id=?
                """)) {
            statement.setString(1, status.name()); setDate(statement, 2, settlementDate);
            statement.setString(3, Instant.now().toString()); statement.setLong(4, id); statement.setLong(5, householdId);
            if (statement.executeUpdate() != 1) throw new SQLException("Transaction status was not updated");
        } catch (SQLException exception) { throw failure("change transaction status", exception); }
    }

    @Override public DashboardSummary summarize(long householdId, YearMonth month, Clock clock) {
        String start = month.atDay(1).toString(), end = month.plusMonths(1).atDay(1).toString();
        String today = LocalDate.now(clock).toString();
        long received=0, expected=0, paid=0, pending=0, overdue=0, projectedIncome=0, projectedExpense=0;
        String aggregate = """
                SELECT
                COALESCE(SUM(CASE WHEN transaction_type='INCOME' AND status='SETTLED' THEN amount_cents ELSE 0 END),0) received,
                COALESCE(SUM(CASE WHEN transaction_type='INCOME' AND status='PENDING' THEN amount_cents ELSE 0 END),0) expected,
                COALESCE(SUM(CASE WHEN transaction_type='EXPENSE' AND status='SETTLED' THEN amount_cents ELSE 0 END),0) paid,
                COALESCE(SUM(CASE WHEN transaction_type='EXPENSE' AND status='PENDING' THEN amount_cents ELSE 0 END),0) pending,
                COALESCE(SUM(CASE WHEN transaction_type='EXPENSE' AND status='PENDING' AND due_date<? THEN amount_cents ELSE 0 END),0) overdue,
                COALESCE(SUM(CASE WHEN transaction_type='INCOME' AND status!='CANCELLED' THEN amount_cents ELSE 0 END),0) projected_income,
                COALESCE(SUM(CASE WHEN transaction_type='EXPENSE' AND status!='CANCELLED' THEN amount_cents ELSE 0 END),0) projected_expense
                FROM transactions WHERE household_id=? AND reference_date>=? AND reference_date<?
                """;
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        List<Transaction> upcoming = new ArrayList<>();
        try (var connection = connections.openConnection()) {
            try (var statement = connection.prepareStatement(aggregate)) {
                statement.setString(1,today); statement.setLong(2,householdId); statement.setString(3,start); statement.setString(4,end);
                try (var r=statement.executeQuery()) { received=r.getLong("received"); expected=r.getLong("expected"); paid=r.getLong("paid"); pending=r.getLong("pending"); overdue=r.getLong("overdue"); projectedIncome=r.getLong("projected_income"); projectedExpense=r.getLong("projected_expense"); }
            }
            try (var statement = connection.prepareStatement("""
                    SELECT c.name, SUM(t.amount_cents) total FROM transactions t JOIN categories c ON c.id=t.category_id
                    WHERE t.household_id=? AND t.transaction_type='EXPENSE' AND t.status!='CANCELLED'
                    AND t.reference_date>=? AND t.reference_date<? GROUP BY c.id,c.name ORDER BY total DESC
                    """)) {
                statement.setLong(1,householdId); statement.setString(2,start); statement.setString(3,end);
                try (var r=statement.executeQuery()) { while(r.next()) byCategory.put(r.getString(1), MoneyUtils.fromCents(r.getLong(2))); }
            }
            try (var statement = connection.prepareStatement(SELECT + """
                    WHERE t.household_id=? AND t.transaction_type='EXPENSE' AND t.status='PENDING' AND t.due_date>=?
                    ORDER BY t.due_date LIMIT 5
                    """)) {
                statement.setLong(1,householdId); statement.setString(2,today);
                try (var r=statement.executeQuery()) { while(r.next()) upcoming.add(map(r)); }
            }
        } catch (SQLException exception) { throw failure("calculate dashboard", exception); }
        return new DashboardSummary(MoneyUtils.fromCents(received),MoneyUtils.fromCents(expected),MoneyUtils.fromCents(paid),
                MoneyUtils.fromCents(pending),MoneyUtils.fromCents(overdue),MoneyUtils.fromCents(received-paid),
                MoneyUtils.fromCents(projectedIncome-projectedExpense),byCategory,upcoming);
    }

    private void bindAll(PreparedStatement s, Transaction t, int i) throws SQLException {
        s.setLong(i++,t.householdId()); s.setLong(i++,t.profileId()); s.setLong(i++,t.categoryId()); s.setString(i++,t.type().name());
        s.setString(i++,t.description()); s.setLong(i++,t.amountCents()); s.setString(i++,t.referenceDate().toString());
        setDate(s,i++,t.dueDate()); setDate(s,i++,t.settlementDate()); s.setString(i++,t.status().name()); s.setString(i++,t.notes());
        s.setString(i++,t.createdAt().toString()); s.setString(i,t.updatedAt().toString());
    }
    private void bindValues(PreparedStatement s,List<Object> values)throws SQLException{int i=1;for(Object v:values){if(v instanceof Long l)s.setLong(i++,l);else s.setString(i++,v.toString());}}
    private void setDate(PreparedStatement s,int i,LocalDate d)throws SQLException{if(d==null)s.setNull(i,java.sql.Types.VARCHAR);else s.setString(i,d.toString());}
    private Transaction withId(Transaction t,long id){return new Transaction(id,t.householdId(),t.profileId(),t.categoryId(),t.type(),t.description(),t.amountCents(),t.referenceDate(),t.dueDate(),t.settlementDate(),t.status(),t.notes(),t.createdAt(),t.updatedAt(),t.profileName(),t.categoryName());}
    private Transaction map(ResultSet r)throws SQLException{return new Transaction(r.getLong("id"),r.getLong("household_id"),r.getLong("profile_id"),r.getLong("category_id"),TransactionType.valueOf(r.getString("transaction_type")),r.getString("description"),r.getLong("amount_cents"),LocalDate.parse(r.getString("reference_date")),date(r,"due_date"),date(r,"settlement_date"),TransactionStatus.valueOf(r.getString("status")),r.getString("notes"),Instant.parse(r.getString("created_at")),Instant.parse(r.getString("updated_at")),r.getString("profile_name"),r.getString("category_name"));}
    private LocalDate date(ResultSet r,String name)throws SQLException{String v=r.getString(name);return v==null?null:LocalDate.parse(v);}
    private DatabaseException failure(String action,SQLException e){return new DatabaseException("Could not "+action,e);}
}
