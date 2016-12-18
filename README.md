# S3 Image Converter

A Clojure library designed to grab all images in S3 bucket and apply a transformation to them.

## Usage

Make sure you have your S3 credentials in `~/.aws/credentials`

`lein run`

with options:

`BUCKETNAME=mybucket lein run [cleanup, convert]`

clean: `BUCKETNAME=mybucket lein run --function clean --matcher _dan_test.jpg`

convert: `BUCKETNAME=mybucket lein run --function convert --matcher \.jpg`

convert pngs in directory: `BUCKETNAME=mybucket lein run --function convert --matcher \.png --prefix products`

## Running In Docker

 * add your aws files to a gitignored directory
   * copy from `~/.aws/` to `./deploy`
 * `docker build -t danmayer/clojure-image-mapper:latest .`
 * `docker run -it --rm --name running-image-mapper danmayer/clojure-image-mapper:latest`
 
 To run with arguments and env variables:
 
 `docker run -e BUCKETNAME=mybucket -it --rm --name running-image-mapper danmayer/clojure-image-mapper:latest lein run --function convert --matcher \.jpg`

### dockerhub

* `docker login`
* `docker push danmayer/clojure-image-mapper`

How to make your `Dockerfile` wrap this one with your credentials and do something useful, like say run the converter every 5min in a cron. 


```
#~/my_project/depoy/image-cron
PATH=/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
*/5 * * * * cd /usr/src/app && BUCKETNAME=my_bucker lein run --function convert --matcher \.jpg --prefix products >> /var/log/cron.log 2>&1
```

```
#~/my_project/Dockerfile
FROM danmayer/clojure-image-mapper:latest
MAINTAINER Dan Mayer <dan.mayer@xyz.com>

# add credentials
ADD deploy/aws_credentials /root/.aws/credentials

# Add crontab file in the cron directory
ADD deploy/image-cron /var/spool/cron/crontabs/root

# Give execution rights on the cron job
RUN chmod 0644 /var/spool/cron/crontabs/root
 
# Create the log file to be able to run tail
RUN touch /var/log/cron.log
 
# Run the command on container startup
CMD crond -l 2 -f
```

## Using webp-imageio

The Jar as well as macOS & Linux native library is included in the repo, if it is necessary to rebuild it, do the following:

```
brew install hg gradle cmake
hg clone https://bitbucket.org/luciad/webp-imageio
cd webp-imageio
gradle build -x test
cp ./build/libs/webp-imageio-<VERSION>-SNAPSHOT.jar <path-to-this-project>/resources/
# (update `:resource-paths` in project.clj)

mkdir build
cd build
cmake ..
cmake --build .
cp src/main/c/* <path-to-this-project-repo>/native
```

## Todo

* oneoff script to remove all webp or fix public read on existing ones?
* better way to handle AWS timeouts / throttling
* time based filter options
* more efficient S3 filter query (via CLI opposed to in app)
* don't write files work directly with IO-streams
  * https://github.com/karls/collage/issues/8
  * https://github.com/karls/collage/blob/master/src/fivetonine/collage/util.clj#L82
  * https://github.com/weavejester/clj-aws-s3/blob/master/src/aws/sdk/s3.clj#L134 
* contribute patch back to collage to make WebP work again
  * https://github.com/karls/collage/blob/master/src/fivetonine/collage/util.clj#L35-L80
  * https://bitbucket.org/luciad/webp-imageio/src/fde3644e6aa610f6a8d97c3d982a7c3926324ecf/src/javase/java/com/luciad/imageio/webp/WebPWriteParam.java?at=default&fileviewer=file-view-default#WebPWriteParam.java-28 
* keep a count of how many images you converted
* see about maximizing perf
  *  increasing the buffers a lot to maybe 500 or 1000. 
  *  I also misremembered the semantics of “pipeline-async” and I think you should try replacing them all with “pipeline-blocking” instead since all the IO is blocking. 

## License

Copyright © 2016 Dan Mayer and Ben Brinckerhoff

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
