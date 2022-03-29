#!/bin/sh

VERSION="2.9.5"

docker build -t ruedigergad/bowerick:${VERSION} .
docker tag ruedigergad/bowerick:${VERSION} ruedigergad/bowerick:latest

