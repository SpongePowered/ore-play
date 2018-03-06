FROM openjdk:8-jdk-alpine

MAINTAINER Jadon Fowler <jadonflower@gmail.com>

# Install Activator
RUN apk update
RUN apk add curl unzip bash postgresql
RUN curl -O http://downloads.typesafe.com/typesafe-activator/1.3.6/typesafe-activator-1.3.6.zip
RUN unzip typesafe-activator-1.3.6.zip -d / && rm typesafe-activator-1.3.6.zip && chmod a+x /activator-dist-1.3.6/activator
ENV PATH $PATH:/activator-dist-1.3.6

# Copy Ore
RUN mkdir -p /home/play/ore/
WORKDIR /home/play/ore/
ADD . /home/play/ore/

# Ore runs on port 9000
# 8888 is the Activator UI
EXPOSE 9000

RUN cp conf/application.conf.template conf/application.conf

CMD ["/home/play/ore/docker.sh"]
