CREATE SEQUENCE ticket_reference_seq;

CREATE TABLE tickets (
    id          BIGSERIAL PRIMARY KEY,
    reference   VARCHAR(20)  NOT NULL UNIQUE,
    title       VARCHAR(200) NOT NULL,
    description TEXT         NOT NULL,
    category    VARCHAR(20)  NOT NULL CHECK (category IN ('ACCESS', 'NETWORK', 'HARDWARE', 'SOFTWARE', 'OTHER')),
    priority    VARCHAR(10)  NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status      VARCHAR(20)  NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    requester   VARCHAR(100) NOT NULL,
    resolution  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE ticket_comments (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT       NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    author     VARCHAR(100) NOT NULL,
    content    TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ticket_comments_ticket_idx ON ticket_comments (ticket_id, created_at);
