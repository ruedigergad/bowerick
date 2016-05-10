;;;
;;;   Copyright 2016, Ruediger Gad
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Simple Benchmarks"}  
  bowerick.benchmarks
  (:require
    [bowerick.jms :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.test :refer :all]
    [criterium [core :as cc]])
  (:import
    (com.ning.compress.lzf LZFDecoder LZFEncoder)
    (java.nio.charset Charset)))

(def ^:dynamic *local-jms-server* "tcp://127.0.0.1:42424")
(def test-topic "/topic/testtopic.foo")

(defn benchmark-fixture [t]
  (let [broker (start-broker *local-jms-server*)]
    (t)
    (stop broker)))

(use-fixtures :each benchmark-fixture)

(def nippy-stress-data-benchable
  (dissoc taoensso.nippy/stress-data-benchable :lazy-seq :lazy-seq-empty :sorted-map :sorted-set))

(defn run-benchmarks
  [description producer-factory-fn consumer-factory-fn data]
  (doseq [n [1 4 10 40 100 400]] ;[1 1 2 4 10 20 50 100 200 500]
    (println (str "Running benchmark: " description "-" n))
    (let [consumer (consumer-factory-fn *local-jms-server* test-topic identity n)
          producer (producer-factory-fn *local-jms-server* test-topic n)]
      (cc/with-progress-reporting
        (cc/bench
          (producer data)))
      (close producer)
      (close consumer))))

(deftest ^:benchmark default-serialization-nippy-stress-data-benchmarks
  (run-benchmarks
    "default-serialization_nippy-stress-data"
    create-producer
    create-consumer
    nippy-stress-data-benchable))

;(deftest ^:benchmark pooled-cheshire-nippy-stress-data-transmission-benchmarks
;  (run-benchmarks
;    "pooled-cheshire-nippy-stress-data-transmission"
;    (fn [url ep n]
;      (create-pooled-producer url ep n cheshire.core/generate-string))
;    (fn [url ep cb]
;      (create-pooled-consumer url ep cb cheshire.core/parse-string))
;    nippy-stress-data-benchable))
;
;(deftest ^:benchmark pooled-cheshire-lzf-nippy-stress-data-transmission-benchmarks
;  (let [charset (Charset/forName "UTF-8")]
;    (run-benchmarks
;      "pooled-cheshire-lzf-nippy-stress-data-transmission"
;      (fn [url ep n]
;        (create-pooled-producer
;          url
;          ep
;          n
;          (fn [data]
;            (-> data (cheshire.core/generate-string) (.getBytes charset) (LZFEncoder/encode)))))
;      (fn [url ep cb]
;        (create-pooled-consumer
;          url
;          ep
;          cb
;          (fn [^bytes data]
;            (-> data (LZFDecoder/decode) (String. charset) (cheshire.core/parse-string)))))
;      nippy-stress-data-benchable)))

(deftest ^:benchmark nippy-serialization-nippy-stress-data-benchmarks
  (run-benchmarks
    "nippy-serialization_nippy-stress-data"
    create-nippy-producer
    create-nippy-consumer
    nippy-stress-data-benchable))

(deftest ^:benchmark nippy-lz4-serialization-nippy-stress-data-benchmarks
  (run-benchmarks
    "nippy-lz4-serialization_nippy-stress-data"
    (fn [url ep n]
      (create-nippy-producer url ep n {:compressor taoensso.nippy/lz4-compressor}))
    create-nippy-consumer
    nippy-stress-data-benchable))

(deftest ^:benchmark nippy-lzma2-serialization-nippy-stress-data-benchmarks
  (run-benchmarks
    "nippy-lzma2-serialization_nippy-stress-data"
    (fn [url ep n]
      (create-nippy-producer url ep n {:compressor taoensso.nippy/lzma2-compressor}))
    create-nippy-consumer
    nippy-stress-data-benchable))

(deftest ^:benchmark nippy-snappy-serialization-nippy-stress-data-benchmarks
  (run-benchmarks
    "nippy-snappy-serialization_nippy-stress-data"
    (fn [url ep n]
      (create-nippy-producer url ep n {:compressor taoensso.nippy/snappy-compressor}))
    create-nippy-consumer
    nippy-stress-data-benchable))

(deftest ^:benchmark nippy-lzf-serialization-nippy-stress-data-benchmarks
  (run-benchmarks
    "nippy-lzf-serialization_nippy-stress-data"
    create-nippy-lzf-producer
    create-nippy-lzf-consumer
    nippy-stress-data-benchable))

;(deftest ^:benchmark pooled-carbonite-nippy-stress-data-transmission-benchmarks
;  (run-benchmarks
;    "pooled-carbonite-nippy-stress-data-transmission"
;    create-pooled-carbonite-producer
;    create-pooled-carbonite-consumer
;    nippy-stress-data-benchable))
;
;(deftest ^:benchmark pooled-carbonite-lzf-nippy-stress-data-transmission-benchmarks
;  (run-benchmarks
;    "pooled-carbonite-lzf-nippy-stress-data-transmission"
;    create-pooled-carbonite-lzf-producer
;    create-pooled-carbonite-lzf-consumer
;    nippy-stress-data-benchable))
;
