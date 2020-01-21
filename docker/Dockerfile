FROM openjdk:8-jre-alpine

ARG CLIENT_JAR_FILE
ARG SERVER_JAR_FILE

EXPOSE 8080
EXPOSE 22200
EXPOSE 22201
EXPOSE 22202

RUN mkdir -p /var/lib/chordial-jars


##
## Client stub setup
##
COPY $CLIENT_JAR_FILE /var/lib/chordial-jars/chordial-client.jar
COPY docker/run-client.sh /usr/local/bin/chordial

RUN chmod +x /var/lib/chordial-jars/chordial-client.jar


##
## Server setup
##
COPY $SERVER_JAR_FILE /var/lib/chordial-jars/chordial-server.jar
COPY docker/run-server.sh /usr/local/bin/chordial-app

RUN chmod +x /var/lib/chordial-jars/chordial-server.jar
ENTRYPOINT chordial-app

