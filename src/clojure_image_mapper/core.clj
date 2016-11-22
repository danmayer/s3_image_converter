(ns clojure-image-mapper.core
  (:require [aws.sdk.s3 :as s3]
            [clojure.pprint :as pp]
            [clojure.core.async :as async]
            [clojure.string :as string]
            [fivetonine.collage.util :as util]
            [fivetonine.collage.core :refer :all]
            [clojure.java.io :as io]
            [clojure.java.io :refer [as-file file]])
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

(defn expand-home [s]
  (if (string/starts-with? s "~")
    (string/replace-first s "~" (System/getProperty "user.home"))
    s))

(defn aws-creds []
  (slurp (expand-home "~/.aws/credentials")))

;; add function that works with new webp API
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

;;todo pass in bucketname
;; (Use https://github.com/weavejester/environ
;;  and add instructions for adding .lein-env file that
;;  is ignored by .git)
(def bucket-name "offgridelectricdev")

(defn aws-access-key-id [cred-str]
  (nth (re-find #"(?m)^aws_access_key_id.*=\s(\S+)", cred-str) 1)
)

(defn aws-secret-access-key [cred-str]
  (nth (re-find #"(?m)^aws_secret_access_key.*=\s(\S+)", cred-str) 1)
)

(defn cred [creds-str]
  {:access-key (aws-access-key-id creds-str)
   :secret-key (aws-secret-access-key creds-str)})

(defn entry-list [cred, bucket] (map :key (get (s3/list-objects cred bucket) :objects)))

(defn filtered-image-paths [entry-list]
  ;;(remove #(re-find #"thumb" %)
    (filter #(re-find #"\.jpg" %) entry-list)
  ;;)
  )

(defn removal-image-paths [entry-list]
  (filter #(re-find #"\_dan_test.jpg" %) entry-list))

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
  (let [local-path (string/join "/" ["/tmp", image-path])]
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


(def convert-example
  ;; Working example
  (with-image "./example.jpg"
    (save_webp "./example.webp" :quality 0.9))
  )


(defn convert-images[]
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
                 (filtered-image-paths))]
         (async/>!! ch1 path))
    (async/close! ch1)


    (async/<!! exitchan)
  )
)

(defn clean-up[]
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
                 (removal-image-paths))]
         (async/>!! ch1 path))
    (async/close! ch1)


    (async/<!! exitchan)
  )
)

(defn -main[]
  ;;(clean-up)
  (convert-images)
  (println "done!!!!!!!"))
