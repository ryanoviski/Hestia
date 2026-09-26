package io.github.ryanoviski.hestia.infrastructure.repositories;

import io.github.ryanoviski.hestia.application.dto.GeneratedExpense;
import io.github.ryanoviski.hestia.application.repositories.FinancialCommitmentRepository;
import io.github.ryanoviski.hestia.domain.enums.TransactionStatus;
import io.github.ryanoviski.hestia.domain.models.Installment;
import io.github.ryanoviski.hestia.domain.models.InstallmentPlan;
import io.github.ryanoviski.hestia.domain.models.RecurringExpense;
import io.github.ryanoviski.hestia.infrastructure.database.ConnectionFactory;
import io.github.ryanoviski.hestia.infrastructure.database.DatabaseException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteFinancialCommitmentRepository implements FinancialCommitmentRepository {
    private final ConnectionFactory connections;
    public SqliteFinancialCommitmentRepository(ConnectionFactory connections){this.connections=connections;}

    @Override public RecurringExpense createRecurring(RecurringExpense rule,List<GeneratedExpense> occurrences){
        return transaction("create recurring expense",c->{long id=insertRule(c,rule);var saved=withId(rule,id);insertOccurrences(c,saved,occurrences);return saved;});
    }
    @Override public RecurringExpense updateRecurring(RecurringExpense rule,YearMonth effective,List<GeneratedExpense> future){
        return transaction("update recurring expense",c->{
            try(var s=c.prepareStatement("""
                UPDATE recurring_expenses SET profile_id=?,category_id=?,description=?,amount_cents=?,first_due_date=?,end_date=?,active=?,notes=?,updated_at=? WHERE id=? AND household_id=?
                """)){bindRuleUpdate(s,rule);if(s.executeUpdate()!=1)throw new SQLException("Recurring expense not updated");}
            for(GeneratedExpense item:future){if(item.referenceMonth().isBefore(effective))continue;
                try(var s=c.prepareStatement("""
                    UPDATE transactions SET profile_id=?,category_id=?,description=?,amount_cents=?,reference_date=?,due_date=?,notes=?,updated_at=?
                    WHERE id IN (SELECT o.transaction_id FROM recurring_expense_occurrences o WHERE o.recurring_expense_id=? AND o.reference_month=? AND o.customized=0)
                    AND status='PENDING'
                    """)){s.setLong(1,rule.profileId());s.setLong(2,rule.categoryId());s.setString(3,rule.description());s.setLong(4,rule.amountCents());s.setString(5,item.dueDate().toString());s.setString(6,item.dueDate().toString());s.setString(7,rule.notes());s.setString(8,rule.updatedAt().toString());s.setLong(9,rule.id());s.setString(10,item.referenceMonth().toString());s.executeUpdate();}
            }
            insertOccurrences(c,rule,future);return rule;});
    }
    @Override public List<RecurringExpense> findRecurring(long householdId,String search,Long profileId,Long categoryId){
        StringBuilder sql=new StringBuilder("""
            SELECT r.*,p.name profile_name,COALESCE(cp.name,c.name) category_name,
            (SELECT MIN(t.due_date) FROM recurring_expense_occurrences o JOIN transactions t ON t.id=o.transaction_id WHERE o.recurring_expense_id=r.id AND t.status='PENDING') next_due
            FROM recurring_expenses r JOIN profiles p ON p.id=r.profile_id JOIN categories c ON c.id=r.category_id
            LEFT JOIN category_preferences cp ON cp.category_id=c.id AND cp.household_id=r.household_id
            WHERE r.household_id=?
            """);List<Object> values=new ArrayList<>();values.add(householdId);
        if(search!=null&&!search.isBlank()){sql.append(" AND lower(r.description) LIKE lower(?)");values.add("%"+search.trim()+"%");}
        if(profileId!=null){sql.append(" AND r.profile_id=?");values.add(profileId);}if(categoryId!=null){sql.append(" AND r.category_id=?");values.add(categoryId);}sql.append(" ORDER BY r.active DESC,r.description");
        try(var c=connections.openConnection();var s=c.prepareStatement(sql.toString())){bind(s,values);try(var rs=s.executeQuery()){List<RecurringExpense> out=new ArrayList<>();while(rs.next())out.add(mapRule(rs));return out;}}
        catch(SQLException e){throw failure("find recurring expenses",e);}
    }
    @Override public Optional<RecurringExpense> findRecurringById(long householdId,long id){return findRecurring(householdId,null,null,null).stream().filter(r->r.id()==id).findFirst();}
    @Override public void appendRecurringOccurrences(RecurringExpense rule,List<GeneratedExpense> occurrences){transaction("generate recurring occurrences",c->{insertOccurrences(c,rule,occurrences);return null;});}
    @Override public void setRecurringActive(long householdId,long id,boolean active,boolean cancelFuture,LocalDate today){transaction("change recurring expense",c->{
        try(var s=c.prepareStatement("UPDATE recurring_expenses SET active=?,updated_at=? WHERE id=? AND household_id=?")){s.setInt(1,active?1:0);s.setString(2,Instant.now().toString());s.setLong(3,id);s.setLong(4,householdId);if(s.executeUpdate()!=1)throw new SQLException("Recurring expense not found");}
        if(cancelFuture)try(var s=c.prepareStatement("""
            UPDATE transactions SET status='CANCELLED',settlement_date=NULL,updated_at=? WHERE status='PENDING' AND due_date>=? AND id IN
            (SELECT transaction_id FROM recurring_expense_occurrences WHERE recurring_expense_id=? AND customized=0)
            """)){s.setString(1,Instant.now().toString());s.setString(2,today.toString());s.setLong(3,id);s.executeUpdate();}return null;});}

    @Override public InstallmentPlan createInstallmentPlan(InstallmentPlan plan,List<GeneratedExpense> items){return transaction("create installment plan",c->{
        long id;try(var s=c.prepareStatement("""
            INSERT INTO installment_plans(household_id,profile_id,category_id,description,total_amount_cents,installment_count,first_due_date,active,notes,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)
            """,Statement.RETURN_GENERATED_KEYS)){s.setLong(1,plan.householdId());s.setLong(2,plan.profileId());s.setLong(3,plan.categoryId());s.setString(4,plan.description());s.setLong(5,plan.totalAmountCents());s.setInt(6,plan.installmentCount());s.setString(7,plan.firstDueDate().toString());s.setInt(8,1);s.setString(9,plan.notes());s.setString(10,plan.createdAt().toString());s.setString(11,plan.updatedAt().toString());s.executeUpdate();id=key(s);}
        for(GeneratedExpense item:items){long tx=insertTransaction(c,plan.householdId(),plan.profileId(),plan.categoryId(),item,plan.createdAt());try(var s=c.prepareStatement("INSERT INTO installments(installment_plan_id,transaction_id,installment_number,planned_amount_cents,created_at) VALUES(?,?,?,?,?)")){s.setLong(1,id);s.setLong(2,tx);s.setInt(3,item.sequence());s.setLong(4,item.amountCents());s.setString(5,plan.createdAt().toString());s.executeUpdate();}}
        return new InstallmentPlan(id,plan.householdId(),plan.profileId(),plan.categoryId(),plan.description(),plan.totalAmountCents(),plan.installmentCount(),plan.firstDueDate(),true,plan.notes(),plan.createdAt(),plan.updatedAt(),null,null,0,plan.installmentCount(),0,0,plan.totalAmountCents(),plan.firstDueDate());});}
    @Override public List<InstallmentPlan> findInstallmentPlans(long householdId){String sql="""
        SELECT ip.*,p.name profile_name,COALESCE(cp.name,c.name) category_name,
        COALESCE(SUM(CASE WHEN t.status='SETTLED' THEN 1 ELSE 0 END),0) paid_count,
        COALESCE(SUM(CASE WHEN t.status='PENDING' THEN 1 ELSE 0 END),0) pending_count,
        COALESCE(SUM(CASE WHEN t.status='CANCELLED' THEN 1 ELSE 0 END),0) cancelled_count,
        COALESCE(SUM(CASE WHEN t.status='SETTLED' THEN t.amount_cents ELSE 0 END),0) paid_amount,
        COALESCE(SUM(CASE WHEN t.status='PENDING' THEN t.amount_cents ELSE 0 END),0) remaining_amount,
        MIN(CASE WHEN t.status='PENDING' THEN t.due_date END) next_due
        FROM installment_plans ip JOIN profiles p ON p.id=ip.profile_id JOIN categories c ON c.id=ip.category_id
        LEFT JOIN category_preferences cp ON cp.category_id=c.id AND cp.household_id=ip.household_id
        JOIN installments i ON i.installment_plan_id=ip.id JOIN transactions t ON t.id=i.transaction_id
        WHERE ip.household_id=? GROUP BY ip.id ORDER BY ip.created_at DESC
        """;try(var c=connections.openConnection();var s=c.prepareStatement(sql)){s.setLong(1,householdId);try(var rs=s.executeQuery()){List<InstallmentPlan> out=new ArrayList<>();while(rs.next())out.add(mapPlan(rs));return out;}}catch(SQLException e){throw failure("find installment plans",e);}}
    @Override public List<Installment> findInstallments(long householdId,long planId){String sql="""
        SELECT i.*,t.due_date,t.status FROM installments i JOIN installment_plans p ON p.id=i.installment_plan_id JOIN transactions t ON t.id=i.transaction_id WHERE p.household_id=? AND p.id=? ORDER BY i.installment_number
        """;try(var c=connections.openConnection();var s=c.prepareStatement(sql)){s.setLong(1,householdId);s.setLong(2,planId);try(var rs=s.executeQuery()){List<Installment> out=new ArrayList<>();while(rs.next())out.add(new Installment(rs.getLong("id"),planId,rs.getLong("transaction_id"),rs.getInt("installment_number"),rs.getLong("planned_amount_cents"),LocalDate.parse(rs.getString("due_date")),TransactionStatus.valueOf(rs.getString("status"))));return out;}}catch(SQLException e){throw failure("find installments",e);}}
    @Override public void cancelRemainingInstallments(long householdId,long planId,LocalDate today){transaction("cancel remaining installments",c->{try(var s=c.prepareStatement("""
        UPDATE transactions SET status='CANCELLED',settlement_date=NULL,updated_at=? WHERE status='PENDING' AND due_date>=? AND id IN
        (SELECT i.transaction_id FROM installments i JOIN installment_plans p ON p.id=i.installment_plan_id WHERE p.id=? AND p.household_id=?)
        """)){s.setString(1,Instant.now().toString());s.setString(2,today.toString());s.setLong(3,planId);s.setLong(4,householdId);s.executeUpdate();}try(var s=c.prepareStatement("UPDATE installment_plans SET active=0,updated_at=? WHERE id=? AND household_id=?")){s.setString(1,Instant.now().toString());s.setLong(2,planId);s.setLong(3,householdId);s.executeUpdate();}return null;});}

    @Override public boolean deleteInactiveRecurring(long householdId,long id){return transaction("delete inactive recurring expense",c->{
        if(!inactiveExists(c,"recurring_expenses",householdId,id))return false;
        try(var links=c.prepareStatement("DELETE FROM recurring_expense_occurrences WHERE recurring_expense_id=?")){links.setLong(1,id);links.executeUpdate();}
        try(var rule=c.prepareStatement("DELETE FROM recurring_expenses WHERE id=? AND household_id=? AND active=0")){rule.setLong(1,id);rule.setLong(2,householdId);return rule.executeUpdate()==1;}
    });}

    @Override public boolean deleteInactiveInstallmentPlan(long householdId,long id){return transaction("delete inactive installment plan",c->{
        if(!inactiveExists(c,"installment_plans",householdId,id))return false;
        try(var links=c.prepareStatement("DELETE FROM installments WHERE installment_plan_id=?")){links.setLong(1,id);links.executeUpdate();}
        try(var plan=c.prepareStatement("DELETE FROM installment_plans WHERE id=? AND household_id=? AND active=0")){plan.setLong(1,id);plan.setLong(2,householdId);return plan.executeUpdate()==1;}
    });}

    private long insertRule(Connection c,RecurringExpense r)throws SQLException{try(var s=c.prepareStatement("""
        INSERT INTO recurring_expenses(household_id,profile_id,category_id,description,amount_cents,first_due_date,end_date,active,notes,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)
        """,Statement.RETURN_GENERATED_KEYS)){s.setLong(1,r.householdId());s.setLong(2,r.profileId());s.setLong(3,r.categoryId());s.setString(4,r.description());s.setLong(5,r.amountCents());s.setString(6,r.firstDueDate().toString());s.setString(7,r.endDate()==null?null:r.endDate().toString());s.setInt(8,r.active()?1:0);s.setString(9,r.notes());s.setString(10,r.createdAt().toString());s.setString(11,r.updatedAt().toString());s.executeUpdate();return key(s);}}
    private void insertOccurrences(Connection c,RecurringExpense r,List<GeneratedExpense> items)throws SQLException{for(GeneratedExpense item:items){if(!r.active()||(r.endDate()!=null&&item.dueDate().isAfter(r.endDate())))continue;boolean exists;try(var s=c.prepareStatement("SELECT 1 FROM recurring_expense_occurrences WHERE recurring_expense_id=? AND reference_month=?")){s.setLong(1,r.id());s.setString(2,item.referenceMonth().toString());try(var rs=s.executeQuery()){exists=rs.next();}}if(exists)continue;long tx=insertTransaction(c,r.householdId(),r.profileId(),r.categoryId(),item,Instant.now());try(var s=c.prepareStatement("INSERT INTO recurring_expense_occurrences(recurring_expense_id,transaction_id,reference_month,customized,created_at) VALUES(?,?,?,?,?)")){s.setLong(1,r.id());s.setLong(2,tx);s.setString(3,item.referenceMonth().toString());s.setInt(4,0);s.setString(5,Instant.now().toString());s.executeUpdate();}}}
    private long insertTransaction(Connection c,long household,long profile,long category,GeneratedExpense i,Instant now)throws SQLException{try(var s=c.prepareStatement("""
        INSERT INTO transactions(household_id,profile_id,category_id,transaction_type,description,amount_cents,reference_date,due_date,settlement_date,status,notes,created_at,updated_at) VALUES(?,?,?,'EXPENSE',?,?,?,?,NULL,'PENDING',?,?,?)
        """,Statement.RETURN_GENERATED_KEYS)){s.setLong(1,household);s.setLong(2,profile);s.setLong(3,category);s.setString(4,i.description());s.setLong(5,i.amountCents());s.setString(6,i.dueDate().toString());s.setString(7,i.dueDate().toString());s.setString(8,i.notes());s.setString(9,now.toString());s.setString(10,now.toString());s.executeUpdate();return key(s);}}
    private void bindRuleUpdate(java.sql.PreparedStatement s,RecurringExpense r)throws SQLException{s.setLong(1,r.profileId());s.setLong(2,r.categoryId());s.setString(3,r.description());s.setLong(4,r.amountCents());s.setString(5,r.firstDueDate().toString());s.setString(6,r.endDate()==null?null:r.endDate().toString());s.setInt(7,r.active()?1:0);s.setString(8,r.notes());s.setString(9,r.updatedAt().toString());s.setLong(10,r.id());s.setLong(11,r.householdId());}
    private RecurringExpense mapRule(ResultSet r)throws SQLException{return new RecurringExpense(r.getLong("id"),r.getLong("household_id"),r.getLong("profile_id"),r.getLong("category_id"),r.getString("description"),r.getLong("amount_cents"),LocalDate.parse(r.getString("first_due_date")),date(r,"end_date"),r.getInt("active")==1,r.getString("notes"),Instant.parse(r.getString("created_at")),Instant.parse(r.getString("updated_at")),r.getString("profile_name"),r.getString("category_name"),date(r,"next_due"));}
    private InstallmentPlan mapPlan(ResultSet r)throws SQLException{return new InstallmentPlan(r.getLong("id"),r.getLong("household_id"),r.getLong("profile_id"),r.getLong("category_id"),r.getString("description"),r.getLong("total_amount_cents"),r.getInt("installment_count"),LocalDate.parse(r.getString("first_due_date")),r.getInt("active")==1,r.getString("notes"),Instant.parse(r.getString("created_at")),Instant.parse(r.getString("updated_at")),r.getString("profile_name"),r.getString("category_name"),r.getInt("paid_count"),r.getInt("pending_count"),r.getInt("cancelled_count"),r.getLong("paid_amount"),r.getLong("remaining_amount"),date(r,"next_due"));}
    private RecurringExpense withId(RecurringExpense r,long id){return new RecurringExpense(id,r.householdId(),r.profileId(),r.categoryId(),r.description(),r.amountCents(),r.firstDueDate(),r.endDate(),r.active(),r.notes(),r.createdAt(),r.updatedAt(),r.profileName(),r.categoryName(),r.nextDueDate());}
    private LocalDate date(ResultSet r,String n)throws SQLException{String v=r.getString(n);return v==null?null:LocalDate.parse(v);}private long key(java.sql.PreparedStatement s)throws SQLException{try(var k=s.getGeneratedKeys()){if(!k.next())throw new SQLException("No identifier returned");return k.getLong(1);}}
    private void bind(java.sql.PreparedStatement s,List<Object> values)throws SQLException{int i=1;for(Object v:values){if(v instanceof Long l)s.setLong(i++,l);else s.setString(i++,v.toString());}}
    private boolean inactiveExists(Connection c,String table,long householdId,long id)throws SQLException{try(var s=c.prepareStatement("SELECT 1 FROM "+table+" WHERE id=? AND household_id=? AND active=0")){s.setLong(1,id);s.setLong(2,householdId);try(var r=s.executeQuery()){return r.next();}}}
    private <T>T transaction(String action,SqlWork<T> work){try(var c=connections.openConnection()){c.setAutoCommit(false);try{T result=work.run(c);c.commit();return result;}catch(Exception e){c.rollback();throw e;}}catch(Exception e){if(e instanceof DatabaseException d)throw d;throw failure(action,e);}}
    private DatabaseException failure(String action,Exception e){return new DatabaseException("Could not "+action,e);}@FunctionalInterface private interface SqlWork<T>{T run(Connection c)throws Exception;}
}
