# This Dockerfile is designed to be used for production
FROM openjdk:8-jdk-alpine

LABEL maintainer="Jadon Fowler <jadonflower@gmail.com>"

# Temporary build folder for the 'stage' task
WORKDIR /home/ore/build
# The .dockerignore file on the project root avoids build cache and personal configuration to be included in the image
ADD . ./

# TODO use Docker secrets for the app key and passwords (and any other sensible information)
ENV SBT_VERSION=1.2.1 \
    SBT_HOME=/usr/local/sbt \
    JDBC_DATABASE_URL=jdbc:postgresql://db/ore \
    SPONGE_AUTH_URL=http://spongeauth:8000 \
    APPLICATION_SECRET="some_secret"

ENV PATH=${PATH}:${SBT_HOME}/bin

# TODO a shell script to extract the SBT version from project/build.properties and set SBT_VERSION to the output value
RUN cp conf/application.conf.template conf/application.conf
RUN apk update
RUN apk add --virtual --no-cache curl ca-certificates bash

# Downloads SBT with the version given above and extracts it
RUN curl -sL "https://piccolo.link/sbt-$SBT_VERSION.tgz" -o "sbt-$SBT_VERSION.tgz"
RUN tar -xvzf "sbt-$SBT_VERSION.tgz" -C /usr/local

# Compiles Ore and makes a production distribution (but not in an archive, unlike 'dist')
RUN sbt stage
RUN mkdir -p /home/ore/prod

# Copy the 'stage' task result _content_ into the production directory
RUN cp -r /home/ore/build/target/universal/stage/* /home/ore/prod

# Cleans the temporary build directory, as we don't need it in the final image
RUN rm -rf /home/ore/build

# SBT is no longer needed too
RUN rm -rf $SBT_HOME
RUN apk del curl

WORKDIR /home/ore/prod/bin

# Ore runs on port 9000
EXPOSE 9000

CMD ["./ore"]
