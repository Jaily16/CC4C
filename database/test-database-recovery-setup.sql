-- Run once as a MySQL administrator after a failed migration of cc4c_test.
-- The recovery database name is deliberately fixed so restore tooling can reject
-- accidental writes to any other schema.
CREATE DATABASE cc4c_recovery_test
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
    ON cc4c_recovery_test.* TO 'cc4c_test_user'@'127.0.0.1';
