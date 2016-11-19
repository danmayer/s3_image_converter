(ns clojure-image-mapper.core-test
  (:require [clojure.test :refer :all]
            [clojure-image-mapper.core :refer :all]))

(deftest test-aws-access-key-id
  (is (= nil (aws-access-key-id "")))
  (is (= nil (aws-access-key-id "aws_access_key_id =   ")))
  (is (= "foobar" (aws-access-key-id "aws_access_key_id = foobar"))))

(deftest test-aws-secret-access-key
  (is (= nil (aws-secret-access-key "")))
  (is (= nil (aws-secret-access-key "aws_secret_access_key =   ")))
  (is (= "foobar" (aws-secret-access-key "aws_secret_access_key = foobar"))))
