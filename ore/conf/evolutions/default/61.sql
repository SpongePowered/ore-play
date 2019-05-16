# --- !Ups

alter table projects add column is_sponge_plugin boolean not null default false;
alter table projects add column is_forge_mod boolean not null default false;

# --- !Downs

alter table projects drop column is_sponge_plugin;
alter table projects drop column is_forge_mod;
