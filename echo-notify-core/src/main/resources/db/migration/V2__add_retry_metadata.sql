ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS last_error_category VARCHAR(100);
