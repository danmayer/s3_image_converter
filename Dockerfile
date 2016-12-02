FROM clojure

RUN apt-get update -yq && apt-get upgrade -yq
RUN apt-get update
RUN apt-get install -y libpq-dev gcc cmake build-essential openjdk-8-jre

RUN update-alternatives --config java
RUN echo `java -showversion`

RUN mkdir /builds
WORKDIR /builds
RUN hg clone https://bitbucket.org/luciad/webp-imageio
WORKDIR /builds/webp-imageio
RUN mkdir build
WORKDIR /builds/webp-imageio/build
RUN cmake ..
RUN cmake --build .
RUN echo `ls /builds/webp-imageio/build/src/main/c/`

RUN mkdir /root/.aws
ADD deploy/config /root/.aws/config
ADD deploy/credentials /root/.aws/credentials

COPY . /usr/src/app
WORKDIR /usr/src/app

RUN cp /builds/webp-imageio/build/src/main/c/libwebp-imageio.so /usr/src/app/native/

RUN lein deps

CMD ["lein", "run"]