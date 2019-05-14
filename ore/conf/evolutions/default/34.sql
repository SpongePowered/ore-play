# --- !Ups

update projects set description = null where char_length(description) > 120;
alter table projects alter column description set data type varchar(120);

# --- !Downs
