FROM openjdk:24-jdk-bookworm

ARG VERSION

WORKDIR /bowerick
COPY dist/bowerick-$VERSION-standalone.jar .
COPY entrypoint.sh .

ENTRYPOINT ["./entrypoint.sh"]]
