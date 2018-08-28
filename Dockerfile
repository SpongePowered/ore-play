FROM openjdk:8-jdk-alpine

LABEL maintainer="Jadon Fowler <jadonflower@gmail.com>"

# Temporary build folder for the 'stage' task
WORKDIR /home/ore/build
ADD . ./
ADD conf/application.conf.template conf/application.conf

ENV SBT_VERSION=0.13.9 \
    SBT_HOME=/usr/local/sbt \
    JDBC_DATABASE_URL=jdbc:postgresql://db/ore \
    SPONGE_AUTH_URL=http://spongeauth:8000 \
    APPLICATION_SECRET="some_secret"

ENV PATH=${PATH}:${SBT_HOME}/bin

RUN apk add --virtual --no-cache curl ca-certificates bash && \
    curl -sL "http://dl.bintray.com/sbt/native-packages/sbt/$SBT_VERSION/sbt-$SBT_VERSION.tgz" | gunzip | tar -x -C /usr/local && \
    sbt stage && \
    apk del curl

WORKDIR /home/ore/prod/bin/

RUN cp -r /home/ore/build/target/universal/stage/* /home/ore/prod/ && \
    rm -rf /home/ore/build/

# Ore runs on port 9000
EXPOSE 9000

CMD ["./ore"]
