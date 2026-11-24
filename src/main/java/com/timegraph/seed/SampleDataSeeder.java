package com.timegraph.seed;

import com.timegraph.engine.DatabaseEngine;
import com.timegraph.model.ColumnDef;
import com.timegraph.model.DataType;
import com.timegraph.model.TableSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SampleDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);

    public void seedIfEmpty(DatabaseEngine engine) {
        if (!engine.isInitialized()) {
            log.info("Initializing new TimeGraph repository and seeding sample data...");
            engine.init();
            seedSampleData(engine);
        } else if (engine.getHeadCommitHash() == null) {
            log.info("Repository exists but has no commits. Seeding sample dataset...");
            seedSampleData(engine);
        } else {
            log.info("TimeGraph repository already initialized at branch '{}' with HEAD={}",
                    engine.getCurrentBranch(), engine.getHeadCommitHash());
        }
    }

    public synchronized void resetAndSeed(DatabaseEngine engine) {
        log.info("Resetting repository and re-seeding demo data...");
        engine.clear();
        seedSampleData(engine);
    }

    private void seedSampleData(DatabaseEngine engine) {
        // 1. Create employees table schema
        List<ColumnDef> columns = List.of(
                new ColumnDef("id", DataType.INT, true),
                new ColumnDef("name", DataType.VARCHAR, false),
                new ColumnDef("department", DataType.VARCHAR, false),
                new ColumnDef("salary", DataType.DOUBLE, false),
                new ColumnDef("active", DataType.BOOLEAN, false)
        );
        TableSchema schema = new TableSchema(columns, "id");
        engine.createTable("employees", schema);

        // 2. Insert baseline employees
        upsertEmployee(engine, 1, "Alice Smith", "Engineering", 95000.0, true);
        upsertEmployee(engine, 2, "Bob Jones", "Marketing", 70000.0, true);
        upsertEmployee(engine, 3, "Charlie Brown", "Engineering", 105000.0, true);
        upsertEmployee(engine, 4, "Diana Prince", "Human Resources", 68000.0, true);

        // 3. Initial commit on main
        engine.commit("Initial commit: Create employees table with baseline staff", "Sarah Connor");

        // 4. Create branch 'salary-hike'
        engine.createBranch("salary-hike");
        engine.checkout("salary-hike");

        // 5. Apply raises and onboard Evan Wright
        upsertEmployee(engine, 1, "Alice Smith", "Engineering", 110000.0, true); // +$15k
        upsertEmployee(engine, 3, "Charlie Brown", "Engineering", 122000.0, true); // +$17k
        upsertEmployee(engine, 5, "Evan Wright", "Design", 82000.0, true); // new hire
        engine.commit("Apply annual 15% salary hike to engineering and onboard Evan Wright", "Engineering Lead");

        // 6. Switch back to 'main'
        engine.checkout("main");

        // 7. On main: update Bob and add Frank Castle
        upsertEmployee(engine, 2, "Bob Jones", "Marketing", 76000.0, true); // cost of living adjustment
        upsertEmployee(engine, 6, "Frank Castle", "Security", 89000.0, true); // security hire
        engine.commit("Add Frank Castle to security and grant Bob cost-of-living bump", "HR Director");

        log.info("Sample database successfully seeded with branches 'main' and 'salary-hike'!");
    }

    private void upsertEmployee(DatabaseEngine engine, int id, String name, String dept, double salary, boolean active) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("name", name);
        values.put("department", dept);
        values.put("salary", salary);
        values.put("active", active);
        engine.upsert("employees", values);
    }
}
