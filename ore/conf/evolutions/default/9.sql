# --- !Ups

UPDATE users SET roles = '{1}' WHERE username = 'gabizou';

# --- !Downs

UPDATE users SET roles = '{}' WHERE username = 'gabizou';
