# --- !Ups

CREATE TABLE project_competitions
(
    id                     BIGSERIAL           NOT NULL PRIMARY KEY,
    created_at             TIMESTAMP           NOT NULL,
    user_id                BIGINT              NOT NULL REFERENCES users ON DELETE RESTRICT,
    name                   VARCHAR(255) UNIQUE NOT NULL,
    time_zone              VARCHAR(255)        NOT NULL,
    description            TEXT,
    start_date             TIMESTAMP           NOT NULL,
    end_date               TIMESTAMP           NOT NULL,
    is_voting_enabled      BOOLEAN             NOT NULL DEFAULT TRUE,
    is_staff_voting_only   BOOLEAN             NOT NULL DEFAULT FALSE,
    should_show_vote_count BOOLEAN             NOT NULL DEFAULT TRUE,
    is_sponge_only         BOOLEAN             NOT NULL DEFAULT FALSE,
    is_source_required     BOOLEAN             NOT NULL DEFAULT FALSE,
    default_votes          INT                 NOT NULL DEFAULT 1,
    staff_votes            INT                 NOT NULL DEFAULT 1,
    allowed_entries        INT                 NOT NULL DEFAULT 1,
    max_entry_total        INT
);

CREATE TABLE project_competition_entries
(
    id             BIGSERIAL NOT NULL PRIMARY KEY,
    created_at     TIMESTAMP NOT NULL,
    project_id     BIGINT    NOT NULL REFERENCES projects ON DELETE CASCADE,
    user_id        BIGINT    NOT NULL REFERENCES users ON DELETE CASCADE,
    competition_id BIGINT    NOT NULL REFERENCES project_competitions ON DELETE CASCADE,
    UNIQUE (project_id, user_id, competition_id)
);

CREATE TABLE project_competition_entry_votes
(
    user_id  BIGSERIAL NOT NULL REFERENCES users ON DELETE CASCADE,
    entry_id BIGSERIAL NOT NULL REFERENCES project_competition_entries ON DELETE CASCADE,
    PRIMARY KEY (user_id, entry_id)
);

# --- !Downs

DROP TABLE project_competition_entry_votes;
DROP TABLE project_competition_entries;

DROP TABLE project_competitions;