(ns clojure-image-mapper.core
  (:require [aws.sdk.s3 :as s3]
            [clojure.pprint :as pp]
            [clojure.core.async :as async]
            [clojure.string :as string]
            [fivetonine.collage.util :as util]
            [fivetonine.collage.core :refer :all]
            [clojure.java.io :as io]))

;;todo move to ~/.aws... needs file expansion
(def aws-creds (slurp "/Users/danmayer/.aws/credentials"))

(def aws-access-key-id
  (nth (re-find #"(?m)^aws_access_key_id.*=\s(.*)", aws-creds) 1)
)

(def aws-secret-access-key
  (nth (re-find #"(?m)^aws_secret_access_key.*=\s(.*)", aws-creds) 1)
)

(def cred {:access-key aws-access-key-id, :secret-key aws-secret-access-key})

(defn entry-list [cred, bucket] (map :key (get (s3/list-objects cred bucket) :objects)))

(defn filtered-image-paths [entry-list] (filter #(re-find #"\.jpg" %) entry-list))

(defn convert-image [image-path]
  (println image-path) 
  (let [converted-path (string/replace image-path #"\.jpg" ".webp")]
     (println converted-path)
     (with-image image-path
            (resize :width 100)
            (rotate 90)
            (util/save converted-path :quality 0.85))
     converted-path
  )
)

(defn write-to-s3 [cred image-path local-path]
  (reduce + (range 0 9000000))
  (let [converted-path (string/replace image-path #"\.jpg" ".webp")]
     (println converted-path)
     converted-path
  )
)

(defn read-from-s3 [cred bucket image-path]
  (let [local-path (string/join "/" ["/tmp", image-path])]
    (clojure.java.io/make-parents local-path)
    (io/copy
     (io/input-stream (:content (s3/get-object cred bucket image-path)))
     (io/output-stream local-path))
    local-path))

;;add method to clean up temp files

(defn -main[]
  (let [ch1 (async/chan 500)
       ch2 (async/chan 500)
       exitchan (async/chan)]

    (async/thread
       (loop[]
         (when-let [path (async/<!! ch2)]
           (println path)
           (recur)
         )
       )
       (async/close! exitchan) 
    )

;;    (async/pipeline-async 8 ch2 (fn [path c]
;;        (async/>!! c (write-to-s3 cred path "batman"))
;;        (async/close! c)
;;      ) ch1
;;    )

;;    (async/pipeline-async 8 ch2 (fn [path c]
;;        (async/>!! c (convert-image cred path "batman"))
;;        (async/close! c)
;;      ) ch1
;;    )

    (async/pipeline-async 8 ch2 (fn [path c]
        (async/>!! c (read-from-s3 cred "offgridelectricdev" path))
        (async/close! c)
      ) ch1
    )

    ;;todo pass in bucketname
    (doseq [path (->>
                 (entry-list cred "offgridelectricdev")
                 (filtered-image-paths))]
         (async/>!! ch1 path))
    (async/close! ch1)


    (async/<!! exitchan)
    (println "done!!!!!!!")
  )

)
