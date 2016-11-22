# clojure_image_mapper

A Clojure library designed to grab all images in S3 bucket and apply a transformation to them.

## Usage

Make sure you have your S3 credentials in `~/.aws/credentials`

`lein run`

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

## License

Copyright © 2016 Dan Mayer and Ben Brinckerhoff

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
