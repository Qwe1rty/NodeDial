CHORDIAL_VERSION = 1.2.2

DOCKER_SERVER = chordial/server
DOCKER_CLIENT = chordial/client


####################
## Build commands ##
####################

clean:
	@sbt clean

compile: clean
	@sbt assembly

docker:
	@docker build \
		--build-arg JAR_FILE=$(shell find . -name "*Server-assembly-*.jar") \
		--file docker/Dockerfile.server \
		--tag $(DOCKER_SERVER):latest \
		--tag $(DOCKER_SERVER):$(CHORDIAL_VERSION) \
		.
#		. & \
#	docker build \
#		--build-arg JAR_FILE=$(shell find . -name "*Client-assembly-*.jar") \
#		--file docker/Dockerfile.client \
#		--tag $(DOCKER_CLIENT):latest \
#		--tag $(DOCKER_CLIENT):$(CHORDIAL_VERSION) \
#		.

all: compile docker


##################
## Run Commands ##
##################

run-server:
	@docker logs -f $(shell docker run -d -p 8080:8080 $(DOCKER_SERVER):latest)

log-server:
	@docker logs -f $(shell docker ps -q --filter ancestor="$(DOCKER_SERVER):latest")

kill-server:
	@docker stop $(shell docker ps -q --filter ancestor="$(DOCKER_SERVER):latest")

run-client:
	@java -jar $(shell find . -name "*Client-assembly-*.jar")
#
#kill-client:
#	docker stop $(shell docker ps -q --filter ancestor="$(DOCKER_CLIENT):latest")