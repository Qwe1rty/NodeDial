#!/usr/bin/env sh

# This version of the script is intended to be used on any non-busybox sh reliant
# system (as the #!/busybox/sh shebang is absolutely needed on the docker version)

java -jar "$(find /var/lib -name "chordial-client.jar" 2> /dev/null)" "$@"

