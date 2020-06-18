CHORDIAL_VERSION = 2.0.0

DOCKER_SERVER = chordial/server
DOCKER_CLIENT = chordial/client

ROOT_LOCATION = /var/lib/chordial
JAR_LOCATION  = $(ROOT_LOCATION)-jars


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
	@DOCKER_BUILDKIT=1 docker build \
		--build-arg SERVER_JAR_FILE=$(shell find . -name "ChordialServer-assembly-*.jar") \
		--build-arg CLIENT_JAR_FILE=$(shell find . -name "ChordialClient-assembly-*.jar") \
		--file docker/Dockerfile \
		--tag $(DOCKER_SERVER):latest \
		--tag $(DOCKER_SERVER):$(CHORDIAL_VERSION) \
		.

all: build docker

## NOTE: sudo permissions required to install global client and server instance
install:
	@sudo mkdir -p $(JAR_LOCATION)

	@sudo rm -f $(JAR_LOCATION)/chordial-client.jar
	@sudo cp -f $(shell find . -name "ChordialClient-assembly-*.jar") $(JAR_LOCATION)/chordial-client.jar		
	@sudo cp -f docker/run-client-local.sh /usr/local/bin/chordial

	@sudo rm -f $(JAR_LOCATION)/chordial-server.jar
	@sudo cp -f $(shell find . -name "ChordialServer-assembly-*.jar") $(JAR_LOCATION)/chordial-server.jar
	@sudo cp -f docker/run-server.sh /usr/local/bin/chordial-app


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
	@docker exec -it $(shell docker ps -q --filter ancestor="$(DOCKER_SERVER):latest") sh

kill-server:
	@docker stop $(shell docker ps -q --filter ancestor="$(DOCKER_SERVER):latest")


######################
## Cluster Commands ##
######################

kube-headless:
	@kubectl create -f kube/chordial-headless.yaml

kube-statefulset:
	@kubectl create -f kube/chordial-statefulset.yaml

kube-ns:
	@kubectl get all -n chordial-ns

kube-ns-create:
	@kubectl create namespace chordial-ns

kube-clear:
	@kubectl delete statefulset cdb -n chordial-ns
	@kubectl delete service chs -n chordial-ns
	@kubectl delete pvc chordial-volume-claim-cdb-0 -n chordial-ns
	@kubectl delete pvc chordial-volume-claim-cdb-1 -n chordial-ns

