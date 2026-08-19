ALTER TABLE users
    ADD CONSTRAINT uk_users_provider_identity UNIQUE (provider, provider_id);
