package ru.vkr.materials;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.vkr.materials.model.*;
import ru.vkr.materials.repository.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BatchNumbersIT {
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager transactions;
    @Autowired
    MaterialBatchRepository batches;
    @Autowired
    MaterialRepository materials;

    private Long materialId() {
        return materials.findAll().getFirst().getId();
    }

    @Test
    void databaseGeneratesTwelveDigitNumberAndHibernateReadsItBack() {
        new TransactionTemplate(transactions).executeWithoutResult(s -> {
            MaterialBatch first = new MaterialBatch();
            first.setMaterial(materials.findById(materialId()).orElseThrow());
            first.setSupplierBatchNumber("FACTORY-001");
            batches.saveAndFlush(first);
            MaterialBatch second = new MaterialBatch();
            second.setMaterial(first.getMaterial());
            batches.saveAndFlush(second);
            assertTrue(first.getBatchNumber().matches("[0-9]{12}"));
            assertEquals(Long.parseLong(first.getBatchNumber()) + 1, Long.parseLong(second.getBatchNumber()));
            assertEquals(first.getBatchNumber(), first.getBarcode());
            assertEquals("FACTORY-001", first.getSupplierBatchNumber());
            s.setRollbackOnly();
        });
    }

    @Test
    void concurrentInsertsReceiveDifferentNumbersAndRollbackDoesNotReuseThem() throws Exception {
        Long material = materialId();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++)
                results.add(pool.submit(() -> jdbc.queryForObject(
                        "INSERT INTO material_batches(materials_id) VALUES (?) RETURNING batch_number", String.class,
                        material)));
            Set<String> unique = new HashSet<>();
            for (var result : results)
                assertTrue(unique.add(result.get(10, TimeUnit.SECONDS)));
            assertEquals(8, unique.size());
            String rolledBack = new TransactionTemplate(transactions).execute(s -> {
                String number = jdbc.queryForObject(
                        "INSERT INTO material_batches(materials_id) VALUES (?) RETURNING batch_number", String.class,
                        material);
                s.setRollbackOnly();
                return number;
            });
            String next = jdbc.queryForObject(
                    "INSERT INTO material_batches(materials_id) VALUES (?) RETURNING batch_number", String.class,
                    material);
            assertTrue(Long.parseLong(next) > Long.parseLong(rolledBack));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void migrationPreservesNumericLegacyNumbersAndConvertsDuplicatesAndText() throws Exception {
        String migration;
        try (var input = getClass().getResourceAsStream("/db/migration/V7__sequential_batch_numbers.sql")) {
            migration = new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
        }
        String schema = "migration_check_" + UUID.randomUUID().toString().replace("-", "");
        new TransactionTemplate(transactions).executeWithoutResult(s -> {
            jdbc.execute("CREATE SCHEMA " + schema);
            jdbc.execute("SET LOCAL search_path TO " + schema);
            jdbc.execute(
                    "CREATE TABLE material_batches(id_material_batches BIGINT PRIMARY KEY,batch_number VARCHAR(100) NOT NULL)");
            jdbc.update(
                    "INSERT INTO material_batches VALUES (1,'24102026500'),(2,'024102026500'),(3,'LOT-ABC'),(4,'000'),(5,'9999999999999')");
            jdbc.execute(migration);
            var rows = jdbc.queryForList(
                    "SELECT batch_number,supplier_batch_number FROM material_batches ORDER BY id_material_batches");
            assertEquals("024102026500", rows.get(0).get("batch_number"));
            assertEquals("024102026501", rows.get(1).get("batch_number"));
            assertEquals("024102026504", rows.get(4).get("batch_number"));
            assertEquals("LOT-ABC", rows.get(2).get("supplier_batch_number"));
            assertEquals("024102026505",
                    jdbc.queryForObject(
                            "INSERT INTO material_batches(id_material_batches) VALUES (6) RETURNING batch_number",
                            String.class));
            s.setRollbackOnly();
        });
    }
}
