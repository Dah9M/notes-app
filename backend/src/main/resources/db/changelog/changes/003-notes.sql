--liquibase formatted sql

--changeset notesapp:003-create-notes
CREATE TABLE notes (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(256) NOT NULL,
    content TEXT,
    owner_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

--changeset notesapp:003-notes-owner-index
CREATE INDEX idx_notes_owner_id ON notes (owner_id);
