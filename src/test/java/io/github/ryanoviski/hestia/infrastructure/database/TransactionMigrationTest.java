package io.github.ryanoviski.hestia.infrastructure.database;

import io.github.ryanoviski.hestia.infrastructure.migrations.MigrationRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionMigrationTest {
    @TempDir Path directory;

    @Test
    void upgradesVersionOneDatabaseWithoutLosingProfiles() throws Exception {
        ConnectionFactory factory = newFactory();
        try (var connection = factory.openConnection()) {
            executeResource(connection, "/db/migrations/V001__initial_schema.sql");
            connection.createStatement().execute("CREATE TABLE schema_history(version INTEGER PRIMARY KEY,description TEXT NOT NULL,applied_at TEXT NOT NULL)");
            connection.createStatement().execute("INSERT INTO schema_history VALUES(1,'initial schema','2026-01-01T00:00:00Z')");
            connection.createStatement().execute("INSERT INTO households VALUES(1,'Família','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')");
            connection.createStatement().execute("INSERT INTO profiles VALUES(1,1,'Ana','PERSON','#123456',1,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')");
        }

        new MigrationRunner(factory).migrate();
        new MigrationRunner(factory).migrate();

        try (var connection=factory.openConnection()) {
            assertThat(queryInt(connection,"SELECT COUNT(*) FROM schema_history")).isEqualTo(5);
            assertThat(queryInt(connection,"SELECT COUNT(*) FROM profiles WHERE name='Ana'")).isEqualTo(1);
            assertThat(queryInt(connection,"SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='transactions'")).isEqualTo(1);
            assertThat(queryInt(connection,"SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='recurring_expenses'")).isEqualTo(1);
        }
    }

    @Test
    void enforcesTransactionConstraintsAndForeignKeys() throws Exception {
        ConnectionFactory factory=newFactory(); new DatabaseInitializer(factory).initialize();
        try(var connection=factory.openConnection()) {
            connection.createStatement().execute("INSERT INTO profiles(household_id,name,profile_type,active,created_at,updated_at) VALUES(1,'Ana','PERSON',1,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')");
            long category=queryLong(connection,"SELECT id FROM categories WHERE category_type='INCOME' LIMIT 1");
            assertThatThrownBy(()->insert(connection,category,"OTHER","PENDING",100)).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->insert(connection,category,"INCOME","OTHER",100)).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->insert(connection,category,"INCOME","PENDING",0)).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->insert(connection,999999,"INCOME","PENDING",100)).isInstanceOf(SQLException.class);
        }
    }

    private ConnectionFactory newFactory() throws Exception { Path db=directory.resolve("data").resolve("test.db");Files.createDirectories(db.getParent());return new ConnectionFactory(db); }
    private void executeResource(java.sql.Connection connection,String path)throws Exception{try(var stream=getClass().getResourceAsStream(path)){String sql=new String(stream.readAllBytes(),StandardCharsets.UTF_8);for(String part:sql.lines().filter(line->!line.stripLeading().startsWith("--")).reduce("",(a,b)->a+b+'\n').split(";")){if(!part.isBlank())connection.createStatement().execute(part);}}}
    private int queryInt(java.sql.Connection c,String sql)throws SQLException{try(var r=c.createStatement().executeQuery(sql)){return r.getInt(1);}}
    private long queryLong(java.sql.Connection c,String sql)throws SQLException{try(var r=c.createStatement().executeQuery(sql)){return r.getLong(1);}}
    private void insert(java.sql.Connection c,long category,String type,String status,long amount)throws SQLException{c.createStatement().execute("INSERT INTO transactions(household_id,profile_id,category_id,transaction_type,description,amount_cents,reference_date,status,created_at,updated_at) VALUES(1,1,"+category+",'"+type+"','Teste',"+amount+",'2026-01-01','"+status+"','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')");}
}
