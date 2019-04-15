# --- !Ups
CREATE TABLE logged_actions (
   id         SERIAL PRIMARY KEY,
   created_at TIMESTAMP NOT NULL DEFAULT NOW(),
   user_id    INT REFERENCES users,
   address    INET NOT NULL,
   action     INT,
   action_context INT,
   action_context_id INT,
   new_state  TEXT,
   old_state  TEXT
);

CREATE INDEX i_logged_actions ON logged_actions (action_context, action_context_id);

CREATE VIEW v_logged_actions AS
  SELECT id, created_at, user_id, address, action, action_context, action_context_id, new_state, old_state,
    CASE WHEN action_context = 0 THEN action_context_id
       WHEN action_context = 1 THEN (SELECT project_id FROM project_versions WHERE ID = action_context_id)
       WHEN action_context = 2 THEN (SELECT project_id FROM project_pages WHERE ID = action_context_id)
       ELSE NULL
    END as project_id,
    CASE WHEN action_context = 1 THEN action_context_id
      ELSE NULL
    END as version_id,
    CASE WHEN action_context = 2 THEN action_context_id
      ELSE NULL
    END as page_id
  FROM logged_actions
;

# --- !Downs
DROP TABLE IF EXISTS logged_actions cascade;
