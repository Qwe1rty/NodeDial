CHORDIAL_VERSION = 1.0.0

DOCKER_TAG = nodedial

ROOT_LOCATION = /var/lib/nodedial
JAR_LOCATION  = $(ROOT_LOCATION)-jars


####################
## Build commands ##
####################

clean:
	@sbt clean

compile: clean
	@sbt compile

build: clean
	@sbt assembly

build-client:
	@sbt "project client" clean assembly

build-server:
	@sbt "project server" clean assembly

.PHONY: docker
docker:
	@DOCKER_BUILDKIT=1 docker build \
		--build-arg SERVER_JAR_FILE=$(shell find . -name "NodeDialServer-assembly-*.jar") \
		--build-arg CLIENT_JAR_FILE=$(shell find . -name "NodeDialClient-assembly-*.jar") \
		--file docker/Dockerfile \
		--tag $(DOCKER_TAG):latest \
		--tag $(DOCKER_TAG):$(CHORDIAL_VERSION) \
		.

all: build docker

## NOTE: sudo permissions required to install global client and server instance
install:
	@sudo mkdir -p $(JAR_LOCATION)

	@sudo rm -f $(JAR_LOCATION)/nodedial-client.jar
	@sudo cp -f $(shell find . -name "NodeDialClient-assembly-*.jar") $(JAR_LOCATION)/nodedial-client.jar
	@sudo cp -f docker/run-client-local.sh /usr/local/bin/nodedial

	@sudo rm -f $(JAR_LOCATION)/nodedial-server.jar
	@sudo cp -f $(shell find . -name "NodeDialServer-assembly-*.jar") $(JAR_LOCATION)/nodedial-server.jar
	@sudo cp -f docker/run-server.sh /usr/local/bin/nodedial-app


##################
## Run Commands ##
##################

run-server:
	@docker logs -f $(shell docker run \
		-d \
		-e SELF_IP='0.0.0.0' \
		-p 8080:8080 $(DOCKER_TAG):latest \
		)

log-server:
	@docker logs -f $(shell docker ps -q --filter ancestor="$(DOCKER_TAG):latest")

exec-server:
	@docker exec -it $(shell docker ps -q --filter ancestor="$(DOCKER_TAG):latest") sh

kill-server:
	@docker stop $(shell docker ps -q --filter ancestor="$(DOCKER_TAG):latest")


######################
## Cluster Commands ##
######################

kube-headless:
	@kubectl create -f kube/nodedial-headless.yaml

kube-statefulset:
	@kubectl create -f kube/nodedial-statefulset.yaml

kube-ns:
	@kubectl get all -n nodedial-ns

kube-ns-create:
	@kubectl create namespace nodedial-ns

kube-clear:
	@kubectl delete statefulset ndb -n nodedial-ns
	@kubectl delete service nhs -n nodedial-ns
	@kubectl delete pvc nodedial-volume-claim-ndb-0 -n nodedial-ns
	@kubectl delete pvc nodedial-volume-claim-ndb-1 -n nodedial-ns
	@kubectl delete pvc nodedial-volume-claim-ndb-2 -n nodedial-ns

