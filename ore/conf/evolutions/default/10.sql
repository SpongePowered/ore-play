# --- !Ups

UPDATE users SET roles = '{3}' WHERE username = 'gabizou' OR username = 'Zidane';

# --- !Downs

UPDATE users SET roles = '{}';
