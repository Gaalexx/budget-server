ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS client_local_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_transactions_user_client_local_id
    ON transactions(user_id, client_local_id)
    WHERE client_local_id IS NOT NULL;
