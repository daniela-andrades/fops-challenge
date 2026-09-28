# Use an official Ubuntu runtime as a parent image
FROM ubuntu:22.04

# install dependencies
RUN apt-get update \
  && apt-get upgrade -y \
  && apt-get install -y --no-install-recommends \
  unzip \
  python3 \
  wget \
  openjdk-11-jdk \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*

RUN ln -sf /usr/bin/python3 /usr/bin/python

# Set the working directory
WORKDIR /root
RUN wget -q https://github.com/playframework/play1/releases/download/1.7.1/play-1.7.1.zip
RUN unzip -q play-1.7.1.zip
RUN mv play-1.7.1 /opt/play
RUN rm play-1.7.1.zip

RUN useradd -m play
RUN chown -R play /opt/play
RUN chgrp -R play /opt/play
RUN chmod +x /opt/play/play

USER play

WORKDIR /home/play/fops

CMD bash start.sh
