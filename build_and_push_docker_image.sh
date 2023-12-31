#!/bin/sh

VERSION="2.9.8"

docker build --build-arg VERSION=${VERSION} -t ruedigergad/bowerick:${VERSION} .
docker tag ruedigergad/bowerick:${VERSION} ruedigergad/bowerick:latest

docker push ruedigergad/bowerick:${VERSION} docker://docker.io/ruedigergad/bowerick:${VERSION}
docker push ruedigergad/bowerick:latest docker://docker.io/ruedigergad/bowerick:latest

