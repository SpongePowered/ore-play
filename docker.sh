#!/bin/sh

# We want to connect to db/ore
env JDBC_DATABASE_URL="jdbc:postgresql://db/ore" SPONGE_AUTH_URL="http://spongeauth:8000" activator run
