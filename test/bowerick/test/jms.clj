;;;
;;;   Copyright 2014, University of Applied Sciences Frankfurt am Main
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Tests for JMS interaction"}  
  bowerick.test.jms
  (:require
    [bowerick.jms :refer :all]
    [cheshire.core :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.test :refer :all] clojure.test))



(def local-jms-server "tcp://127.0.0.1:42424")
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (start-broker local-jms-server)]
    (t)
    (stop broker)))

(use-fixtures :each test-with-broker)



(deftest test-create-topic
  (let [producer (create-single-producer local-jms-server test-topic)]
    (is (not (nil? producer)))
    (close producer)))

(deftest custom-transformation-producer-cheshire
  (let [producer (create-single-producer local-jms-server test-topic generate-string)
        received (ref nil)
        flag (prepare-flag)
        consume-fn (fn [obj] (dosync (ref-set received obj)) (set-flag flag))
        consumer (create-single-consumer local-jms-server test-topic consume-fn)]
    (producer {"a" "A", "b" 123})
    (await-flag flag)
    (is (= "{\"a\":\"A\",\"b\":123}" @received))
    (close producer)
    (close consumer)))

(deftest custom-transformation-producer-consumer-cheshire
  (let [producer (create-single-producer local-jms-server test-topic generate-string)
        received (ref nil)
        flag (prepare-flag)
        consume-fn (fn [obj] (dosync (ref-set received obj)) (set-flag flag))
        consumer (create-single-consumer local-jms-server test-topic consume-fn parse-string)]
    (producer {"a" "A", "b" 123})
    (await-flag flag)
    (is (= {"a" "A", "b" 123} @received))
    (close producer)
    (close consumer)))

(deftest pooled-producer-normal-consumer
  (let [producer (create-pooled-producer local-jms-server test-topic 3)
        was-run (prepare-flag)
        received (ref nil)
        consume-fn (fn [obj] (dosync (ref-set received obj)) (set-flag was-run))
        consumer (create-single-consumer local-jms-server test-topic consume-fn)]
    (producer "a")
    (producer "b")
    (producer "c")
    (await-flag was-run)
    (is (flag-set? was-run))
    (is (= '("a" "b" "c") @received))
    (close producer)
    (close consumer)))

(deftest pooled-producer-pooled-consumer
  (let [producer (create-pooled-producer local-jms-server test-topic 3)
        was-run (prepare-flag 3)
        received (ref [])
        consume-fn (fn [obj] (dosync (alter received conj obj)) (set-flag was-run))
        consumer (create-pooled-consumer local-jms-server test-topic consume-fn)]
    (producer "a")
    (producer "b")
    (producer "c")
    (await-flag was-run)
    (is (flag-set? was-run))
    (is (= ["a" "b" "c"] @received))
    (close producer)
    (close consumer)))

(deftest pooled-producer-pooled-consumer-nippy
  (let [producer (create-pooled-nippy-producer local-jms-server test-topic 3)
        was-run (prepare-flag 3)
        received (ref [])
        consume-fn (fn [obj] (dosync (alter received conj obj)) (set-flag was-run))
        consumer (create-pooled-nippy-consumer local-jms-server test-topic consume-fn)]
    (producer "a")
    (producer "b")
    (producer "c")
    (await-flag was-run)
    (is (flag-set? was-run))
    (is (= ["a" "b" "c"] @received))
    (close producer)
    (close consumer)))

(deftest pooled-producer-pooled-consumer-nippy-lz4
  (let [producer (create-pooled-nippy-producer
                   local-jms-server
                   test-topic
                   3
                   {:compressor taoensso.nippy/lz4-compressor})
        was-run (prepare-flag 3)
        received (ref [])
        consume-fn (fn [obj] (dosync (alter received conj obj)) (set-flag was-run))
        consumer (create-pooled-nippy-consumer local-jms-server test-topic consume-fn)]
    (producer "a")
    (producer "b")
    (producer "c")
    (await-flag was-run)
    (is (flag-set? was-run))
    (is (= ["a" "b" "c"] @received))
    (close producer)
    (close consumer)))

(deftest pooled-producer-pooled-consumer-nippy-snappy
  (let [producer (create-pooled-nippy-producer
                   local-jms-server
                   test-topic
                   3
                   {:compressor taoensso.nippy/snappy-compressor})
        was-run (prepare-flag 3)
        received (ref [])
        consume-fn (fn [obj] (dosync (alter received conj obj)) (set-flag was-run))
        consumer (create-pooled-nippy-consumer local-jms-server test-topic consume-fn)]
    (producer "a")
    (producer "b")
    (producer "c")
    (await-flag was-run)
    (is (flag-set? was-run))
    (is (= ["a" "b" "c"] @received))
    (close producer)
    (close consumer)))

(deftest pooled-producer-pooled-consumer-nippy-lzma2
  (let [producer (create-pooled-nippy-producer
                   local-jms-server
                   test-topic
                   3
                   {:compressor taoensso.nippy/lzma2-compressor})
        was-run (prepare-flag 3)
        received (ref [])
        consume-fn (fn [obj] (dosync (alter received conj obj)) (set-flag was-run))
        consumer (create-pooled-nippy-consumer local-jms-server test-topic consume-fn)]
    (producer "a")
    (producer "b")
    (producer "c")
    (await-flag was-run)
    (is (flag-set? was-run))
    (is (= ["a" "b" "c"] @received))
    (close producer)
    (close consumer)))

(deftest pooled-producer-pooled-consumer-nippy-lzf
  (let [producer (create-pooled-nippy-lzf-producer local-jms-server test-topic 3)
        was-run (prepare-flag 3)
        received (ref [])
        consume-fn (fn [obj] (dosync (alter received conj obj)) (set-flag was-run))
        consumer (create-pooled-nippy-lzf-consumer local-jms-server test-topic consume-fn)]
    (producer "a")
    (producer "b")
    (producer "c")
    (await-flag was-run)
    (is (flag-set? was-run))
    (is (= ["a" "b" "c"] @received))
    (close producer)
    (close consumer)))

(deftest pooled-producer-pooled-consumer-carbonite
  (let [producer (create-pooled-carbonite-producer local-jms-server test-topic 3)
        was-run (prepare-flag 3)
        received (ref [])
        consume-fn (fn [obj] (dosync (alter received conj obj)) (set-flag was-run))
        consumer (create-pooled-carbonite-consumer local-jms-server test-topic consume-fn)]
    (producer "a")
    (producer "b")
    (producer "c")
    (await-flag was-run)
    (is (flag-set? was-run))
    (is (= ["a" "b" "c"] @received))
    (close producer)
    (close consumer)))

(deftest pooled-producer-pooled-consumer-carbonite-lzf
  (let [producer (create-pooled-carbonite-lzf-producer local-jms-server test-topic 3)
        was-run (prepare-flag 3)
        received (ref [])
        consume-fn (fn [obj] (dosync (alter received conj obj)) (set-flag was-run))
        consumer (create-pooled-carbonite-lzf-consumer local-jms-server test-topic consume-fn)]
    (producer "a")
    (producer "b")
    (producer "c")
    (await-flag was-run)
    (is (flag-set? was-run))
    (is (= ["a" "b" "c"] @received))
    (close producer)
    (close consumer)))

