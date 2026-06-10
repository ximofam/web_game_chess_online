package db.migration;

import org.flywaydb.core.Flyway;

public class FlywayMigration {
    public static void main(String[] args) {
        String dbUrl = "jdbc:postgresql://localhost:5432/" + System.getenv("POSTGRES_DB");
        String username = System.getenv("POSTGRES_USER");
        String password = System.getenv("POSTGRES_PASSWORD");

        Flyway flyway = Flyway.configure()
                .dataSource(dbUrl, username, password)
                .locations("classpath:db/migration")
                .schemas("public")
                .baselineOnMigrate(true)
                .validateOnMigrate(true)
                .load();

        flyway.migrate();
        System.out.println("Migration completed!");
    }
}