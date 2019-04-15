# --- !Ups

alter table users rename name to full_name;
alter table users rename username to name;

alter table organizations rename username to name;

# --- !Downs

alter table users rename name to username;
alter table users rename full_name to name;

alter table organizations rename name to username;
