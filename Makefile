clean:
	@sbt clean

compile: clean
	@sbt compile

build: clean
	@sbt assembly