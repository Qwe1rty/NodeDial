CHORDIAL_VERSION = 1.2.5

DOCKER_SERVER = chordial/server
DOCKER_CLIENT = chordial/client

ROOT_LOCATION = /var/lib/chordial
SERVER_LOCATION = $(ROOT_LOCATION)/server
CLIENT_LOCATION = $(ROOT_LOCATION)/client


####################
## Build commands ##
####################

clean:
	@sbt clean

build: clean
	@sbt assembly

build-client:
	@sbt "project client" clean assembly

build-server:
	@sbt "project server" clean assembly

.PHONY: docker
docker:
	@docker build \
		--build-arg SERVER_JAR_FILE=$(shell find . -name "ChordialServer-assembly-*.jar") \
		--build-arg CLIENT_JAR_FILE=$(shell find . -name "ChordialClient-assembly-*.jar") \
		--file docker/Dockerfile \
		--tag $(DOCKER_SERVER):latest \
		--tag $(DOCKER_SERVER):$(CHORDIAL_VERSION) \
		.

all: build docker


##################
## Run Commands ##
##################

run-server:
	@docker logs -f $(shell docker run \
		-d \
		-e SELF_IP='0.0.0.0' \
		-p 8080:8080 $(DOCKER_SERVER):latest \
		)

log-server:
	@docker logs -f $(shell docker ps -q --filter ancestor="$(DOCKER_SERVER):latest")

exec-server:
	@docker exec -it $(shell docker ps -q --filter ancestor="$(DOCKER_SERVER):latest") /bin/sh

kill-server:
	@docker stop $(shell docker ps -q --filter ancestor="$(DOCKER_SERVER):latest")


## NOTE: sudo permissions required to install global client.
install-client:
	@sudo mkdir -p $(CLIENT_LOCATION)
	@sudo rm -f $(CLIENT_LOCATION)/*
	@sudo cp -f $(shell find . -name "ChordialClient-assembly-*.jar") $(CLIENT_LOCATION)
	@sudo cp -f docker/run-client.sh /usr/local/bin/chordial

#local-client:
#	@java -jar $(shell find . -name "ChordialClient-assembly-*.jar")


######################
## Cluster Commands ##
######################

kube-headless:
	@kubectl create -f kube/chordial-headless.yaml

kube-statefulset:
	@kubectl create -f kube/chordial-statefulset.yaml

kube-clear:
	@kubectl delete statefulset cdb -n chordial-ns
	@kubectl delete pvc chordial-volume-claim-cdb-0 -n chordial-ns
	@kubectl delete service chs -n chordial-ns

