clean:
	@sbt clean

compile: clean
	@sbt compile

build: clean
	@sbt assembly


run-client:
	@java -jar $(shell find . -name "*Client-assembly-*.jar")

run-server:
	@java -jar $(shell find . -name "*Server-assembly-*.jar")
