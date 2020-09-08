## The openjdk:8-jre-alpine image has issues with gRPC
## Check out this link for more info:
##
## https://github.com/infostellarinc/stellarstation-api/commit/8677bd930c0dfd124162673b1d4bb3c49b55d6d4
##
## Debug version is needed to provide a shell, via /busybox/sh
##
FROM gcr.io/distroless/java:8-debug

ARG CLIENT_JAR_FILE
ARG SERVER_JAR_FILE

EXPOSE 8080
EXPOSE 22200
EXPOSE 22201
EXPOSE 22202
EXPOSE 22203

SHELL ["/busybox/sh", "-c"]


##
## Client stub setup
##
COPY $CLIENT_JAR_FILE /var/lib/nodedial-jars/nodedial-client.jar
COPY docker/run-client.sh /usr/local/bin/nodedial

RUN chmod +x /var/lib/nodedial-jars/nodedial-client.jar


##
## Server setup
##
COPY $SERVER_JAR_FILE /var/lib/nodedial-jars/nodedial-server.jar
COPY docker/run-server.sh /usr/local/bin/nodedial-app

RUN chmod +x /var/lib/nodedial-jars/nodedial-server.jar
ENTRYPOINT nodedial-app

