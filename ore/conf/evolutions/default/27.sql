# --- !Ups

alter table versions drop constraint versions_channel_id_version_string_key;
create unique index on versions (project_id, version_string);

# --- !Downs

alter table versions drop constraint versions_project_id_version_string_key;
create unique index versions_channel_id_version_string_key on versions (channel_id, version_string);
