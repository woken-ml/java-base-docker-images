# Pull base image
FROM hbpmip/java-base-build:3.5.0-jdk-8-7

#
# Scala and sbt Dockerfile
# Originally taken from
# https://github.com/hseeberger/scala-sbt
#

MAINTAINER Arnaud Jutzeler <arnaud.jutzeler@chuv.ch>

ENV SCALA_VERSION 2.11.8
ENV SBT_VERSION 0.13.16

# Install sbt dependencies
RUN apk add --no-cache ncurses

# Install sbt
RUN \
  curl -L -o sbt-$SBT_VERSION.tgz https://cocl.us/sbt-$SBT_VERSION.tgz && \
  mkdir -p /opt /usr/share/sbt/ref /usr/share/ivy/ref/repository && \
  tar -x -v -z -C /opt -f sbt-$SBT_VERSION.tgz && \
  rm sbt-$SBT_VERSION.tgz && \
  ln -s /opt/sbt/bin/sbt /usr/local/bin

# Install Scala
## Piping curl directly in tar
RUN \
  curl -fsL http://downloads.typesafe.com/scala/$SCALA_VERSION/scala-$SCALA_VERSION.tgz | tar xfz - -C /opt/ && \
  echo >> /root/.bashrc && \
  echo 'export PATH=~/scala-$SCALA_VERSION/bin:$PATH' >> /root/.bashrc

RUN sbt sbtVersion

WORKDIR /seed

COPY docker/build.sbt /seed/
COPY docker/project/ /seed/project/
COPY docker/src/ /seed/src/

RUN sbt about
RUN sbt compile && rm -rf /seed/target

# Define working directory
WORKDIR /build