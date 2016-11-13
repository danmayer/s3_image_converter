(defproject clojure_image_mapper "0.1.0-SNAPSHOT"
  :description "Image Mapper: convert S3 images"
  :url "http://"
  :main clojure-image-mapper.core
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-aws-s3 "0.3.10"]
                 [org.clojure/core.async "0.2.395"]
                 [fivetonine/collage "0.2.1"]])
                 