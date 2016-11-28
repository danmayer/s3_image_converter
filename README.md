# clojure_image_mapper

A Clojure library designed to grab all images in S3 bucket and apply a transformation to them.

## Usage

Make sure you have your S3 credentials in `~/.aws/credentials`

`lein run`

with options:

`BUCKETNAME=mybucket lein run [cleanup, convert]`

clean: `BUCKETNAME=mybucket lein run --function clean --matcher _dan_test.jpg`
convert: `BUCKETNAME=mybucket lein run --function convert --matcher \.jpg`

## Using webp-imageio

The jar and macOS native library is included in the repo, if it is necessary to rebuild it, do the following:

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

* Exception Handling improvements with Ben
  * previously output exception but halted
  * try catch in methods works but should print which file failed and continue (or add to skipped_list)
* time based filter options
* more efficient S3 filter query (via CLI opposed to in app)
* don't write files work directly with IO-streams
  * https://github.com/karls/collage/issues/8
  * https://github.com/karls/collage/blob/master/src/fivetonine/collage/util.clj#L82
  * https://github.com/weavejester/clj-aws-s3/blob/master/src/aws/sdk/s3.clj#L134 
* contribute patch back to collage to make WebP work again
  * https://github.com/karls/collage/blob/master/src/fivetonine/collage/util.clj#L35-L80
  * https://bitbucket.org/luciad/webp-imageio/src/fde3644e6aa610f6a8d97c3d982a7c3926324ecf/src/javase/java/com/luciad/imageio/webp/WebPWriteParam.java?at=default&fileviewer=file-view-default#WebPWriteParam.java-28 

## License

Copyright © 2016 Dan Mayer and Ben Brinckerhoff

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
