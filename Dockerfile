FROM openjdk:11-jdk-bullseye

ARG VERSION

WORKDIR /bowerick
COPY dist/bowerick-$VERSION-standalone.jar .
COPY entrypoint.sh .

ENTRYPOINT ["./entrypoint.sh"]]
