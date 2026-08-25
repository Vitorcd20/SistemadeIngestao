ALTER TABLE transactions DROP CONSTRAINT transactions_pkey;
ALTER TABLE transactions ADD PRIMARY KEY (id, owner_user_id);
