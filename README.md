# clojure_image_mapper

A Clojure library designed to grab all images in S3 bucket and apply a transformation to them.

## Usage

Make sure you have your S3 credentials in `~/.aws/credentials`

`lein run`



## Using webp-imageio

`webp-imageio.jar` has been copied from `https://github.com/karls/collage` to `resources` and the  `project.clj` file has been updated to use it.

The macOS native library is included in the repo, but if we need to rebuild, here are the steps I took (on macOS):

```
brew install hg gradle
hg clone https://bitbucket.org/luciad/webp-imageio
cd webp-imageio
gradle build -x test
 cp ./build/libs/webp-imageio-<VERSION>-SNAPSHOT.jar <path-to-this-project>/resources
cp target/
mkdir build
cd build
cmake ..
cmake --build .
cp src/main/c/* <path-to-this-project-repo>/native
```

## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
