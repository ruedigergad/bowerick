FROM openjdk:11-jdk-bullseye

WORKDIR /bowerick
COPY dist/bowerick-2.9.6-standalone.jar .
COPY entrypoint.sh .

ENTRYPOINT ["./entrypoint.sh"]]
