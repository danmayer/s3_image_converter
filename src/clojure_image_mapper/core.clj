(ns clojure-image-mapper.core
  (:require [aws.sdk.s3 :as s3]
            [clojure.pprint :as pp]
            [clojure.core.async :as async]
            [clojure.string :as string]
            [fivetonine.collage.util :as util]
            [fivetonine.collage.core :refer :all]
            [clojure.java.io :as io]
            [clojure.java.io :refer [as-file file]]
            [environ.core :refer [env]]
            [clojure.tools.cli :refer [parse-opts]])
  ;; needed to override collage method
   (:import java.io.File
           java.net.URI
           java.net.URL
           java.awt.image.BufferedImage
           javax.imageio.ImageIO
           javax.imageio.IIOImage
           javax.imageio.ImageWriter
           javax.imageio.ImageWriteParam
           com.luciad.imageio.webp.WebPWriteParam
           fivetonine.collage.Frame)
  )


;; https://wyegelwel.github.io/Error-Handling-with-Clojure-Async/
(Thread/setDefaultUncaughtExceptionHandler
	(reify Thread$UncaughtExceptionHandler
		(uncaughtException [_ thread throwable]
      (println "caught exception in Async... Skipping")
			(println (.getMessage throwable))
			)))

(def bucket-name
  (env :bucketname))

(defn expand-home [s]
  (if (string/starts-with? s "~")
    (string/replace-first s "~" (System/getProperty "user.home"))
    s))

(defn aws-creds []
  (slurp (expand-home "~/.aws/credentials")))

;; This function works with new webp API, perhaps fix collage and send a PR
(defn save_webp
  "Store a webp image on disk. Hardcoded for lossless quality
  Accepts optional keyword arguments.
  `:progressive` - boolean, `true` turns progressive saving on, `false`
  turns it off. Defaults to the default value in the ImageIO API -
  `ImageWriteParam/MODE_COPY_FROM_METADATA`. See
  [Java docs](http://docs.oracle.com/javase/7/docs/api/javax/imageio/ImageWriteParam.html).
  Examples:
    (save_webp image \"/path/to/new/image/example.webp\" :progressive false)
  Returns the path to the saved image when saved successfully."
  [^BufferedImage image path & rest]
  (let [opts (apply hash-map rest)
        outfile (file path)
        ext (util/parse-extension path)
        ^ImageWriter writer (.next (ImageIO/getImageWritersByFormatName ext))
        ^ImageWriteParam write-param (.getDefaultWriteParam writer)
        iioimage (IIOImage. image nil nil)
        outstream (ImageIO/createImageOutputStream outfile)]
    (doto write-param
      (.setCompressionType "Lossless")
      (.setCompressionMode ImageWriteParam/MODE_EXPLICIT))
    (when (.canWriteProgressive write-param)
      (let [mode-map {true  ImageWriteParam/MODE_DEFAULT
                      false ImageWriteParam/MODE_DISABLED}
            mode-flag (get opts :progressive)]
        (doto write-param
          (.setProgressiveMode (get mode-map
                                    mode-flag
                                    ImageWriteParam/MODE_COPY_FROM_METADATA)))))
    (doto writer
      (.setOutput outstream)
      (.write nil iioimage write-param)
      (.dispose))
    (.close outstream)
    path))

(defn aws-access-key-id [cred-str]
  (nth (re-find #"(?m)^aws_access_key_id.*=\s(\S+)", cred-str) 1)
)

(defn aws-secret-access-key [cred-str]
  (nth (re-find #"(?m)^aws_secret_access_key.*=\s(\S+)", cred-str) 1)
)

(defn cred [creds-str]
  {:access-key (aws-access-key-id creds-str)
   :secret-key (aws-secret-access-key creds-str)})

(defn entry-list [cred bucket] (map :key (get (s3/list-objects cred bucket) :objects)))

(defn filtered-image-paths [matcher entry-list]
    (filter #(re-find matcher %) entry-list)
  )

(defn removal-image-paths [matcher entry-list]
  (filter #(re-find matcher %) entry-list))

(defn write-to-s3 [cred bucket image-path local-path]
  (let [converted-path (string/replace image-path #"\.jpg" ".webp")]
    (println converted-path)
    (s3/put-object cred bucket converted-path (io/file local-path)
                {:content-type "image/jpg"})
    converted-path
  )
)

(defn convert-image [image-path local-path]
  (let [converted-path (string/replace local-path #"\.jpg" ".webp")]
    ;;(println converted-path)
    (with-image local-path
      (save_webp converted-path :quality 0.9))
    [image-path converted-path]
  )
)

(defn read-from-s3 [cred bucket image-path]
  (let [local-path (string/join "/" ["/tmp/image_mapper/", image-path])]
    (clojure.java.io/make-parents local-path)
    (with-open [in-file (:content (s3/get-object cred bucket image-path))
                 out-file (io/output-stream local-path)]
      ;;(println local-path)
      (io/copy in-file out-file))
    [image-path local-path]))

;;add method to clean up /tmp files


(defn remove-from-s3
  "this removes a file from S3"
  [cred bucket image-path]
  (println image-path)
  (s3/delete-object cred bucket image-path)
  [image-path])

(defn delete-recursively [fname]
  (let [func (fn [func f]
               (when (.isDirectory f)
                 (doseq [f2 (.listFiles f)]
                   (func func f2)))
               (clojure.java.io/delete-file f))]
    (func func (clojure.java.io/file fname))))


(defn convert-images[matcher]
  (let [ch1 (async/chan 8)
       ch2 (async/chan 8)
       ch3 (async/chan 8)
       ch4 (async/chan 8)
        exitchan (async/chan)
        cred (cred (aws-creds))]

    (async/thread
       (loop[]
         (when-let [path (async/<!! ch4)]
           ;;(println path)
           (recur)
         )
       )
       (async/close! exitchan)
    )
    (async/pipeline-async 8 ch2 (fn [path c]
        (async/>!! c (read-from-s3 cred bucket-name path))
        (async/close! c)
      ) ch1
    )

    (async/pipeline-async 8 ch3 (fn [[image-path local-path] c]
       (async/>!! c (convert-image image-path local-path))
       (async/close! c)
     ) ch2
    )

   (async/pipeline-async 8 ch4 (fn [[image-path local-path] c]
       (async/>!! c (write-to-s3 cred bucket-name image-path local-path))
       (async/close! c)
     ) ch3
    )

    (doseq [path (->>
                 (entry-list cred bucket-name)
                 (filtered-image-paths matcher))]
         (async/>!! ch1 path))
    (async/close! ch1)

    (async/<!! exitchan)
    (delete-recursively "/tmp/image_mapper/")
  )
)

(defn clean-up[cleanup-pattern]
    (let [ch1 (async/chan 8)
       ch2 (async/chan 8)
        exitchan (async/chan)
        cred (cred (aws-creds))]

    (async/thread
       (loop[]
         (when-let [path (async/<!! ch2)]
           ;;(println path)
           (recur)
         )
       )
       (async/close! exitchan)
    )

    (async/pipeline-async 8 ch2 (fn [path c]
        (async/>!! c (remove-from-s3 cred bucket-name path))
        (async/close! c)
      ) ch1
    )

    (doseq [path (->>
                 (entry-list cred bucket-name)
                 (removal-image-paths cleanup-pattern))]
         (async/>!! ch1 path))
    (async/close! ch1)


    (async/<!! exitchan)
  )
)

(defn convert-example[]
  ;; Working example
  (with-image "./example.jpg"
    (save_webp "./example.webp" :quality 0.9))
  )

(def cli-options
  ;; An option with a required argument
  [["-f" "--function FUNCTION" "which function clean, convert"
    :default "none"]
   ["-m" "--matcher REGEX" "filter to convert or clean"
    :default "_test.jpg"]
   ["-h" "--help"]])

(defn -main[& args]
  (let [opts (parse-opts args cli-options)]

    (println opts)

    (let [matcher (re-pattern (get-in opts [:options :matcher]))]

      (when (string/includes? (get-in opts [:options :function]) "clean")
        (println "running cleanup...")
        (clean-up matcher))
      (when (string/includes? (get-in opts [:options :function]) "convert")
        (println "running conversion...")
        ;; matcher = #"\.jpg"
        (convert-images matcher))
      (when (string/includes? (get-in opts [:options :function]) "none")
        (println "you must pass in run option --function [clean, convert]")
        (System/exit 0))
    )))
