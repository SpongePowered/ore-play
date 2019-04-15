# --- !Ups

alter table users add column read_prompts int[] not null default '{}';

# --- !Downs

alter table users drop column read_prompts;
