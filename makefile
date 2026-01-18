build:
	./gradlew clean build

run:
	./gradlew clean build
	cd runserver && \
	java -jar ../libs/HytaleServer.jar --allow-op --disable-sentry \
	-assets=/home/adesi/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/package/game/latest/Assets.zip \
	--mods=/mnt/8tbhdd/Projects/Programming/Hytale/Mods/hytale_magical_science/src/main/ --auth-mode=authenticated

all:
	make build run
