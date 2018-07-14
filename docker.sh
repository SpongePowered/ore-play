#!/bin/sh

#groupadd -g "$(stat -c '%g' /home/play/ore/app)" -o spongeauth
#useradd -u "$(stat -c '%u' /home/play/ore/app)" -g spongeauth -o -m spongeauth

activator run
