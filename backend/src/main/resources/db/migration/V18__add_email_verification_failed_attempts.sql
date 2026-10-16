ALTER TABLE email_verifications ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;
