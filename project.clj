(defproject clojure_image_mapper "0.1.0-SNAPSHOT"
  :description "Image Mapper: convert S3 images"
  :url "http://"
  :main clojure-image-mapper.core
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :jvm-opts ["-Djava.library.path=native/"]
  ;;:jvm-opts [~(str "-Djava.library.path=native/:" (System/getenv "LD_LIBRARY_PATH"))]
  :resource-paths ["resources" "resources/webp-imageio-0.5.1-SNAPSHOT.jar"]
  ;;:java-source-paths ["src/fivetonine/collage/java"]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 ;; avoid warning about version ranges
                 [clj-aws-s3 "0.3.10" :exclusions [commons-codec joda-time]]
                 [commons-codec "1.6"]
                 [joda-time "2.2"]
                 [org.clojure/core.async "0.2.395"]
                 [fivetonine/collage "0.2.1"]
                 [environ "1.1.0"]]
  :plugins [[lein-environ "1.1.0"]])
