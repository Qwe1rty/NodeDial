clean:
	@sbt clean

compile: clean
	@sbt compile

build: clean
	@sbt assembly


run-client:
	@java -jar ./server/target/scala-2.13/ChordialClient-assembly-0.0.0.jar

run-server:
	@java -jar ./server/target/scala-2.13/ChordialServer-assembly-0.0.0.jar
