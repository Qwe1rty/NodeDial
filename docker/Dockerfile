FROM openjdk:8-jre-alpine

ARG CLIENT_JAR_FILE
ARG SERVER_JAR_FILE

EXPOSE 8080
EXPOSE 22200
EXPOSE 22201
EXPOSE 22202


##
## Client stub setup
##
RUN mkdir -p /var/lib/chordial/client
COPY $CLIENT_JAR_FILE /var/lib/chordial/client
COPY docker/run-client.sh /usr/local/bin/chordial


##
## Server setup
##
RUN mkdir -p /var/lib/chordial/server
WORKDIR /var/lib/chordial/server
COPY $SERVER_JAR_FILE docker/run-server.sh ./

#RUN dos2unix run-server.sh
ENTRYPOINT ["sh", "/var/lib/chordial/server/run-server.sh"]

