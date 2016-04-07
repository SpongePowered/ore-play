#!/usr/bin/env bash

for i in $(seq 0 200); do
  echo "INSERT INTO projects (created_at, plugin_id, name, owner_name, authors, category_id, views, downloads, starred)" \
        "VALUES (TIMESTAMP '2016-04-06 19:24:15.394', 'pluginId.$i', 'Project $i', 'User $i', '{}', 0, 0, 0, 0);"
done
