#!/usr/bin/env sh

java -jar "$(find /var/lib -name "chordial-client.jar" 2> /dev/null)" "$@"

