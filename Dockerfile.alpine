FROM clojure:alpine

# https://wiki.alpinelinux.org/wiki/How_to_get_regular_stuff_working#Compiling_:_a_few_notes_and_a_reminder
RUN apk update && \
    apk upgrade

RUN mkdir /root/.aws
ADD deploy/config /root/.aws/config
ADD deploy/credentials /root/.aws/credentials

COPY . /usr/src/app
WORKDIR /usr/src/app

RUN lein deps

CMD ["lein", "run"]