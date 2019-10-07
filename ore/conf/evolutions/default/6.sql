# --- !Ups

UPDATE users SET roles = '{0}' WHERE username = 'Grinch' OR username = 'phroa';
UPDATE users SET roles = '{1}' WHERE username = 'windy'  OR username = 'Zidane';
UPDATE users SET roles = '{2}' WHERE username = 'liach'
                                  OR username = 'exidus'
                                  OR username = 'ewized'
                                  OR username = 'dags'
                                  OR username = 'Flibio';

# --- !Downs

UPDATE users SET roles = '{}';
