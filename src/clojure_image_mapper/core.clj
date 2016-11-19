(ns clojure-image-mapper.core
  (:require [aws.sdk.s3 :as s3]
            [clojure.pprint :as pp]
            [clojure.core.async :as async]
            [clojure.string :as string]
            [fivetonine.collage.util :as util]
            [fivetonine.collage.core :refer :all]
            [clojure.java.io :as io]))


(defn expand-home [s]
  (if (.startsWith s "~")
    (string/replace-first s "~" (System/getProperty "user.home"))
    s))

(def aws-creds (slurp (expand-home "~/.aws/credentials")))

;;todo pass in bucketname
(def bucket-name "offgridelectricdev")

(def aws-access-key-id
  (nth (re-find #"(?m)^aws_access_key_id.*=\s(.*)", aws-creds) 1)
)

(def aws-secret-access-key
  (nth (re-find #"(?m)^aws_secret_access_key.*=\s(.*)", aws-creds) 1)
)

(def cred {:access-key aws-access-key-id, :secret-key aws-secret-access-key})

(defn entry-list [cred, bucket] (map :key (get (s3/list-objects cred bucket) :objects)))

(defn filtered-image-paths [entry-list]
  (remove #(re-find #"thumb" %)
    (filter #(re-find #"\.jpg" %) entry-list))
  )

(defn write-to-s3 [cred bucket image-path local-path]
  (let [converted-path (string/replace image-path #"\.jpg" "_dan_test.jpg")]
    (println "************************")
    (println converted-path)
    (println "************************")
    (s3/put-object cred bucket converted-path (io/file local-path)
                {:content-type "image/jpg"})
    converted-path
  )
)

(defn convert-image [image-path local-path]
  (let [converted-path (string/replace local-path #"\.jpg" "_dan_test.jpg")]
     (println converted-path)
     (with-image local-path
            (resize :width 100)
            (rotate 90)
            (util/save converted-path :quality 0.85))
     [image-path converted-path]
  )
)

(defn read-from-s3 [cred bucket image-path]
  (let [local-path (string/join "/" ["/tmp", image-path])]
    (clojure.java.io/make-parents local-path)
    (with-open [in-file (:content (s3/get-object cred bucket image-path))
                 out-file (io/output-stream local-path)]
      (println local-path)
      (io/copy in-file out-file))
    [image-path local-path]))

;;add method to clean up temp files

(defn -main[]
  (let [ch1 (async/chan 8)
       ch2 (async/chan 8)
       ch3 (async/chan 8)
       ch4 (async/chan 8)
       exitchan (async/chan)]

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
    (println "done!!!!!!!")
  )

)
