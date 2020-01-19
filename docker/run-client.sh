#!/usr/bin/env sh

java -jar \
  -Dhandlers=java.util.logging.ConsoleHandler \
  -Dio.grpc.netty.level=INFO \
  -Djava.util.logging.ConsoleHandler.level=INFO \
  -Djava.util.logging.ConsoleHandler.formatter=java.util.logging.SimpleFormatter \
  "$(find /var/lib -name "ChordialClient-assembly-*.jar" 2> /dev/null)" "$@"