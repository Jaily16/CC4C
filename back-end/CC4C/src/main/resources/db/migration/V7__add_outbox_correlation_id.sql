ALTER TABLE async_outbox
    ADD COLUMN correlation_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER event_id;
