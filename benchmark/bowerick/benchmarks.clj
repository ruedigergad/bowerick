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

(deftest ^:benchmark simple-string-transmission-benchmark
  (println "Running benchmark: simple-string-transmission-benchmark")
  (let [producer (create-producer *local-jms-server* test-topic)
        consume-fn (fn [_])
        consumer (create-consumer *local-jms-server* test-topic consume-fn)]
    (cc/with-progress-reporting
      (cc/quick-bench
        (producer "foo-string")))
    (close producer)
    (close consumer)))

(deftest ^:benchmark pooled-string-transmission-benchmark-10-single
  (println "Running benchmark: pooled-string-transmission-benchmark-10-single")
  (let [producer (create-pooled-producer *local-jms-server* test-topic 10)
        consume-fn (fn [_])
        consumer (create-pooled-consumer *local-jms-server* test-topic consume-fn)]
    (cc/with-progress-reporting
      (cc/quick-bench
        (producer "foo-string")))
    (close producer)
    (close consumer)))

(deftest ^:benchmark pooled-nippy-string-transmission-benchmark-10-single
  (println "Running benchmark: pooled-nippy-string-transmission-benchmark-10-single")
  (let [producer (create-pooled-nippy-producer *local-jms-server* test-topic 10)
        consume-fn (fn [_])
        consumer (create-pooled-nippy-consumer *local-jms-server* test-topic consume-fn)]
    (cc/with-progress-reporting
      (cc/quick-bench
        (producer "foo-string")))
    (close producer)
    (close consumer)))

(deftest ^:benchmark pooled-nippy-lzf-string-transmission-benchmark-10-single
  (println "Running benchmark: pooled-nippy-lzf-string-transmission-benchmark-10-single")
  (let [producer (create-pooled-nippy-lzf-producer *local-jms-server* test-topic 10)
        consume-fn (fn [_])
        consumer (create-pooled-nippy-lzf-consumer *local-jms-server* test-topic consume-fn)]
    (cc/with-progress-reporting
      (cc/quick-bench
        (producer "foo-string")))
    (close producer)
    (close consumer)))

(deftest ^:benchmark pooled-string-transmission-benchmarks
  (doseq [n [1 1 1 1 2 3 4 6 8 10 15 20 30 40 50 75 100 150 200 300 400 500 750 1000]]
    (println (str "Running benchmark: pooled-string-transmission-benchmark-" n))
    (let [producer (create-pooled-producer *local-jms-server* test-topic n)
          consume-fn (fn [_])
          consumer (create-pooled-consumer *local-jms-server* test-topic consume-fn)]
      (cc/with-progress-reporting
        (cc/quick-bench
          (producer "foo-string")))
      (close producer)
      (close consumer))))

(deftest ^:benchmark pooled-nippy-string-transmission-benchmarks
  (doseq [n [1 1 1 1 2 3 4 6 8 10 15 20 30 40 50 75 100 150 200 300 400 500 750 1000]]
    (println (str "Running benchmark: pooled-nippy-string-transmission-benchmark-" n))
    (let [producer (create-pooled-nippy-producer *local-jms-server* test-topic n)
          consume-fn (fn [_])
          consumer (create-pooled-nippy-consumer *local-jms-server* test-topic consume-fn)]
      (cc/with-progress-reporting
        (cc/quick-bench
          (producer "foo-string")))
      (close producer)
      (close consumer))))

(deftest ^:benchmark pooled-nippy-lz4-string-transmission-benchmarks
  (doseq [n [1 1 1 1 2 3 4 6 8 10 15 20 30 40 50 75 100 150 200 300 400 500 750 1000]]
    (println (str "Running benchmark: pooled-nippy-lz4-string-transmission-benchmark-" n))
    (let [producer (create-pooled-nippy-producer *local-jms-server* test-topic n {:compressor taoensso.nippy/lz4-compressor})
          consume-fn (fn [_])
          consumer (create-pooled-nippy-consumer *local-jms-server* test-topic consume-fn)]
      (cc/with-progress-reporting
        (cc/quick-bench
          (producer "foo-string")))
      (close producer)
      (close consumer))))

(deftest ^:benchmark pooled-nippy-lzma2-string-transmission-benchmarks
  (doseq [n [1 1 1 1 2 3 4 6 8 10 15 20 30 40 50 75 100 150 200 300 400 500 750 1000]]
    (println (str "Running benchmark: pooled-nippy-lzma2-string-transmission-benchmark-" n))
    (let [producer (create-pooled-nippy-producer *local-jms-server* test-topic n {:compressor taoensso.nippy/lzma2-compressor})
          consume-fn (fn [_])
          consumer (create-pooled-nippy-consumer *local-jms-server* test-topic consume-fn)]
      (cc/with-progress-reporting
        (cc/quick-bench
          (producer "foo-string")))
      (close producer)
      (close consumer))))

(deftest ^:benchmark pooled-nippy-snappy-string-transmission-benchmarks
  (doseq [n [1 1 1 1 2 3 4 6 8 10 15 20 30 40 50 75 100 150 200 300 400 500 750 1000]]
    (println (str "Running benchmark: pooled-nippy-snappy-string-transmission-benchmark-" n))
    (let [producer (create-pooled-nippy-producer *local-jms-server* test-topic n {:compressor taoensso.nippy/snappy-compressor})
          consume-fn (fn [_])
          consumer (create-pooled-nippy-consumer *local-jms-server* test-topic consume-fn)]
      (cc/with-progress-reporting
        (cc/quick-bench
          (producer "foo-string")))
      (close producer)
      (close consumer))))

(deftest ^:benchmark pooled-nippy-lzf-string-transmission-benchmarks
  (doseq [n [1 1 1 1 2 3 4 6 8 10 15 20 30 40 50 75 100 150 200 300 400 500 750 1000]]
    (println (str "Running benchmark: pooled-nippy-lzf-string-transmission-benchmark-" n))
    (let [producer (create-pooled-nippy-lzf-producer *local-jms-server* test-topic n)
          consume-fn (fn [_])
          consumer (create-pooled-nippy-lzf-consumer *local-jms-server* test-topic consume-fn)]
      (cc/with-progress-reporting
        (cc/quick-bench
          (producer "foo-string")))
      (close producer)
      (close consumer))))

