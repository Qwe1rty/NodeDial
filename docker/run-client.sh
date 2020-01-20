#!/bin/sh

java -jar "$(find /var/lib -name "ChordialClient-assembly-*.jar" 2> /dev/null)" "$@"

