FROM openjdk:8-jre-alpine

ARG CLIENT_JAR_FILE
ARG SERVER_JAR_FILE

EXPOSE 8080
EXPOSE 22200
EXPOSE 22201
EXPOSE 22202


RUN mkdir -p /var/lib/chordial/client
COPY $CLIENT_JAR_FILE /var/lib/chordial/client
COPY docker/run-client.sh /usr/local/bin/chordial

RUN mkdir -p /var/lib/chordial/server
WORKDIR /var/lib/chordial/server

COPY $SERVER_JAR_FILE docker/run-server.sh ./
ENTRYPOINT ["./run-server.sh"]