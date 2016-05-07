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
    [criterium [core :as cc]]))

(def ^:dynamic *local-jms-server* "tcp://127.0.0.1:42424")
(def test-topic "/topic/testtopic.foo")

(defn benchmark-fixture [t]
  (let [broker (start-broker *local-jms-server*)]
    (t)
    (.stop broker)))

(use-fixtures :each benchmark-fixture)

(defn run-benchmarks
  [description producer-factory-fn consumer-factory-fn data]
  (doseq [n [1 1 2 4 10 20 50 100 200 400 750 1000]]
    (println (str "Running benchmark: " description "-" n))
    (let [consumer (consumer-factory-fn *local-jms-server* test-topic identity)
          producer (producer-factory-fn *local-jms-server* test-topic n)]
      (cc/with-progress-reporting
        (cc/bench
          (producer data)))
      (close producer)
      (close consumer))))

;(deftest ^:benchmark pooled-string-transmission-benchmarks
;  (run-benchmarks
;    "pooled-string-transmission"
;    create-pooled-producer
;    create-pooled-consumer
;    "foo-string"))
;
;(deftest ^:benchmark pooled-nippy-string-transmission-benchmarks
;  (run-benchmarks
;    "pooled-nippy-string-transmission"
;    create-pooled-nippy-producer
;    create-pooled-nippy-consumer
;    "foo-string"))
;
;(deftest ^:benchmark pooled-nippy-lz4-string-transmission-benchmarks
;  (run-benchmarks
;    "pooled-nippy-lz4-string-transmission"
;    (fn [url ep n]
;      (create-pooled-nippy-producer url ep n {:compressor taoensso.nippy/lz4-compressor}))
;    create-pooled-nippy-consumer
;    "foo-string"))
;
;(deftest ^:benchmark pooled-nippy-lzma2-string-transmission-benchmarks
;  (run-benchmarks
;    "pooled-nippy-lzma2-string-transmission"
;    (fn [url ep n]
;      (create-pooled-nippy-producer url ep n {:compressor taoensso.nippy/lzma2-compressor}))
;    create-pooled-nippy-consumer
;    "foo-string"))
;
;(deftest ^:benchmark pooled-nippy-snappy-string-transmission-benchmarks
;  (run-benchmarks
;    "pooled-nippy-snappy-string-transmission"
;    (fn [url ep n]
;      (create-pooled-nippy-producer url ep n {:compressor taoensso.nippy/snappy-compressor}))
;    create-pooled-nippy-consumer
;    "foo-string"))
;
;(deftest ^:benchmark pooled-nippy-lzf-string-transmission-benchmarks
;  (run-benchmarks
;    "pooled-nippy-lzf-string-transmission"
;    create-pooled-nippy-lzf-producer
;    create-pooled-nippy-lzf-consumer
;    "foo-string"))
;
;(deftest ^:benchmark pooled-carbonite-string-transmission-benchmarks
;  (run-benchmarks
;    "pooled-carbonite-string-transmission"
;    create-pooled-carbonite-producer
;    create-pooled-carbonite-consumer
;    "foo-string"))
;
;(deftest ^:benchmark pooled-carbonite-lzf-string-transmission-benchmarks
;  (run-benchmarks
;    "pooled-carbonite-lzf-string-transmission"
;    create-pooled-carbonite-lzf-producer
;    create-pooled-carbonite-lzf-consumer
;    "foo-string"))

(def nippy-stress-data-benchable
  (dissoc taoensso.nippy/stress-data-benchable :lazy-seq :lazy-seq-empty :sorted-map :sorted-set))

(deftest ^:benchmark pooled-nippy-stress-data-transmission-benchmarks
  (run-benchmarks
    "pooled-nippy-stress-data-transmission"
    create-pooled-producer
    create-pooled-consumer
    nippy-stress-data-benchable))

(deftest ^:benchmark pooled-nippy-nippy-stress-data-transmission-benchmarks
  (run-benchmarks
    "pooled-nippy-nippy-stress-data-transmission"
    create-pooled-nippy-producer
    create-pooled-nippy-consumer
    nippy-stress-data-benchable))

(deftest ^:benchmark pooled-nippy-lz4-nippy-stress-data-transmission-benchmarks
  (run-benchmarks
    "pooled-nippy-lz4-nippy-stress-data-transmission"
    (fn [url ep n]
      (create-pooled-nippy-producer url ep n {:compressor taoensso.nippy/lz4-compressor}))
    create-pooled-nippy-consumer
    nippy-stress-data-benchable))

(deftest ^:benchmark pooled-nippy-lzma2-nippy-stress-data-transmission-benchmarks
  (run-benchmarks
    "pooled-nippy-lzma2-nippy-stress-data-transmission"
    (fn [url ep n]
      (create-pooled-nippy-producer url ep n {:compressor taoensso.nippy/lzma2-compressor}))
    create-pooled-nippy-consumer
    nippy-stress-data-benchable))

(deftest ^:benchmark pooled-nippy-snappy-nippy-stress-data-transmission-benchmarks
  (run-benchmarks
    "pooled-nippy-snappy-nippy-stress-data-transmission"
    (fn [url ep n]
      (create-pooled-nippy-producer url ep n {:compressor taoensso.nippy/snappy-compressor}))
    create-pooled-nippy-consumer
    nippy-stress-data-benchable))

(deftest ^:benchmark pooled-nippy-lzf-nippy-stress-data-transmission-benchmarks
  (run-benchmarks
    "pooled-nippy-lzf-nippy-stress-data-transmission"
    create-pooled-nippy-lzf-producer
    create-pooled-nippy-lzf-consumer
    nippy-stress-data-benchable))

(deftest ^:benchmark pooled-carbonite-nippy-stress-data-transmission-benchmarks
  (run-benchmarks
    "pooled-carbonite-nippy-stress-data-transmission"
    create-pooled-carbonite-producer
    create-pooled-carbonite-consumer
    nippy-stress-data-benchable))

(deftest ^:benchmark pooled-carbonite-lzf-nippy-stress-data-transmission-benchmarks
  (run-benchmarks
    "pooled-carbonite-lzf-nippy-stress-data-transmission"
    create-pooled-carbonite-lzf-producer
    create-pooled-carbonite-lzf-consumer
    nippy-stress-data-benchable))

