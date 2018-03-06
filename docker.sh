#!/bin/sh

echo "Adding SpongeAuth User"
#export PGPASSWORD=ore
until psql -h 'db' -U postgres -c "create role spongeauth with login password 'spongeauth';"; do
  echo "Ore is waiting for PostgreSQL to start." >&2
  sleep 1
done
#groupadd -g "$(stat -c '%g' /home/play/ore/app)" -o spongeauth
#useradd -u "$(stat -c '%u' /home/play/ore/app)" -g spongeauth -o -m spongeauth

activator run
