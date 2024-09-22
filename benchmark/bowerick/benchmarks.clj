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
    [bowerick.jms :as jms]
    [clojure.test :as test]
    [criterium [core :as cc]]
    [taoensso.nippy :as nippy]))

(def ^:dynamic *local-jms-server* "tcp://127.0.0.1:42424")
(def test-topic "/topic/testtopic.foo")

(defn benchmark-fixture [t]
  (let [broker (jms/start-broker *local-jms-server*)]
    (t)
    (jms/stop broker)))

(test/use-fixtures :each benchmark-fixture)

(def nippy-stress-data-benchable
  (dissoc (nippy/stress-data {:comparable true}) :lazy-seq :lazy-seq-empty :sorted-map :sorted-set))

(defn run-benchmarks
  [description producer-factory-fn consumer-factory-fn data]
  (doseq [n [1 4 10 40 100 400]] ;[1 1 2 4 10 20 50 100 200 500]
    (println (str "Running benchmark: " description "-" n))
    (let [consumer (consumer-factory-fn *local-jms-server* test-topic identity n)
          producer (producer-factory-fn *local-jms-server* test-topic n)]
      (cc/with-progress-reporting
        (cc/bench
          (producer data)))
      (jms/close producer)
      (jms/close consumer))))

(test/deftest ^:benchmark default-serialization-nippy-stress-data-benchmarks
  (run-benchmarks
    "default-serialization_nippy-stress-data"
    jms/create-producer
    jms/create-consumer
    nippy-stress-data-benchable))

;(test/deftest ^:benchmark pooled-cheshire-nippy-stress-data-transmission-benchmarks
;  (run-benchmarks
;    "pooled-cheshire-nippy-stress-data-transmission"
;    (fn [url ep n]
;      (jms/create-pooled-producer url ep n cheshire.core/generate-string))
;    (fn [url ep cb]
;      (jms/create-pooled-consumer url ep cb cheshire.core/parse-string))
;    nippy-stress-data-benchable))
;
;(test/deftest ^:benchmark pooled-cheshire-lzf-nippy-stress-data-transmission-benchmarks
;  (let [charset (Charset/forName "UTF-8")]
;    (run-benchmarks
;      "pooled-cheshire-lzf-nippy-stress-data-transmission"
;      (fn [url ep n]
;        (jms/create-pooled-producer
;          url
;          ep
;          n
;          (fn [data]
;            (-> data (cheshire.core/generate-string) (.getBytes charset) (LZFEncoder/encode)))))
;      (fn [url ep cb]
;        (jms/create-pooled-consumer
;          url
;          ep
;          cb
;          (fn [^bytes data]
;            (-> data (LZFDecoder/decode) (String. charset) (cheshire.core/parse-string)))))
;      nippy-stress-data-benchable)))

(test/deftest ^:benchmark nippy-serialization-nippy-stress-data-benchmarks
  (run-benchmarks
    "nippy-serialization_nippy-stress-data"
    jms/create-nippy-producer
    jms/create-nippy-consumer
    nippy-stress-data-benchable))

(test/deftest ^:benchmark nippy-lz4-serialization-nippy-stress-data-benchmarks
  (run-benchmarks
    "nippy-lz4-serialization_nippy-stress-data"
    (fn [url ep n]
      #_{:clj-kondo/ignore [:unresolved-var]}
      (jms/create-nippy-producer url ep n {:compressor nippy/lz4-compressor}))
    jms/create-nippy-consumer
    nippy-stress-data-benchable))

(test/deftest ^:benchmark nippy-lzma2-serialization-nippy-stress-data-benchmarks
  (run-benchmarks
    "nippy-lzma2-serialization_nippy-stress-data"
    (fn [url ep n]
      #_{:clj-kondo/ignore [:unresolved-var]}
      (jms/create-nippy-producer url ep n {:compressor nippy/lzma2-compressor}))
    jms/create-nippy-consumer
    nippy-stress-data-benchable))

(test/deftest ^:benchmark nippy-snappy-serialization-nippy-stress-data-benchmarks
  (run-benchmarks
    "nippy-snappy-serialization_nippy-stress-data"
    (fn [url ep n]
      #_{:clj-kondo/ignore [:unresolved-var]}
      (jms/create-nippy-producer url ep n {:compressor nippy/snappy-compressor}))
    jms/create-nippy-consumer
    nippy-stress-data-benchable))

(test/deftest ^:benchmark nippy-lzf-serialization-nippy-stress-data-benchmarks
  (run-benchmarks
    "nippy-lzf-serialization_nippy-stress-data"
    jms/create-nippy-lzf-producer
    jms/create-nippy-lzf-consumer
    nippy-stress-data-benchable))

(test/deftest ^:benchmark carbonite-serialization-nippy-stress-data-benchmarks
  (run-benchmarks
    "carbonite-serialization_nippy-stress-data"
    jms/create-carbonite-producer
    jms/create-carbonite-consumer
    nippy-stress-data-benchable))

(test/deftest ^:benchmark carbonite-lzf-serialization-nippy-stress-data-benchmarks
  (run-benchmarks
    "carbonite-lzf-serialization_nippy-stress-data"
    jms/create-carbonite-lzf-producer
    jms/create-carbonite-lzf-consumer
    nippy-stress-data-benchable))
