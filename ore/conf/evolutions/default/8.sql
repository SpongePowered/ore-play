# --- !Ups

UPDATE projects SET description = null;
UPDATE users SET roles = '{2}' WHERE username = 'Lergin';
UPDATE users SET roles = '{1}' WHERE username = 'Aaron1011';

# --- !Downs

