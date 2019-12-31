clean:
	@sbt clean

compile: clean
	@sbt assembly

docker:
	docker build \
		--build-arg JAR_FILE=$(shell find . -name "*Server-assembly-*.jar") \
		--file build/docker/Dockerfile.server \
		--tag chordial/server:latest \
		--tag chordial/server:1.1.0 \
		. & \
	docker build \
		--build-arg JAR_FILE=$(shell find . -name "*Client-assembly-*.jar") \
		--file build/docker/Dockerfile.client \
		--tag chordial/client:latest \
		--tag chordial/client:1.1.0 \
		.

all: compile docker


#run-client:
#	@java -jar $(shell find . -name "*Client-assembly-*.jar")

run-server:
	docker logs -f $(shell docker run -d chordial/server:latest)

log-server:
	docker logs -f $(shell docker ps -q --filter ancestor="chordial/server:latest")

#kill-client:
#	docker stop $(shell docker ps -q --filter ancestor="chordial/client:latest")

kill-server:
	docker stop $(shell docker ps -q --filter ancestor="chordial/server:latest")
