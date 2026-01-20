build:
	./gradlew clean build

server:
	./gradlew runServer
	
all:
	make build run
