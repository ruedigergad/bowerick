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
   [bowerick.jms :as jms]
   [bowerick.test.test-helper :as th]
   [cheshire.core :as chsh]
   [clj-assorted-utils.util :as utils]
   [clojure.test :as test]
   [taoensso.nippy :as nippy]))



(def local-jms-server "tcp://127.0.0.1:31314")
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (th/start-test-broker local-jms-server)]
    (t)
    (jms/stop broker)))

(test/use-fixtures :each test-with-broker)



(test/deftest test-create-topic
  (let [producer (jms/create-producer local-jms-server test-topic)]
    (test/is (not (nil? producer)))
    (jms/close producer)))

(test/deftest custom-transformation-producer-cheshire
  (let [producer (jms/create-producer local-jms-server test-topic 1 chsh/generate-string)
        received (ref nil)
        flag (utils/prepare-flag)
        consume-fn (fn [obj] (dosync (ref-set received obj)) (utils/set-flag flag))
        consumer (jms/create-consumer local-jms-server test-topic consume-fn)]
    (producer {"a" "A", "b" 123})
    (utils/await-flag flag)
    (test/is (= "{\"a\":\"A\",\"b\":123}" @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest custom-transformation-producer-consumer-cheshire
  (let [producer (jms/create-producer local-jms-server test-topic 1 chsh/generate-string)
        received (ref nil)
        flag (utils/prepare-flag)
        consume-fn (fn [obj] (dosync (ref-set received obj)) (utils/set-flag flag))
        consumer (jms/create-single-consumer local-jms-server test-topic consume-fn chsh/parse-string)]
    (producer {"a" "A", "b" 123})
    (utils/await-flag flag)
    (test/is (= {"a" "A", "b" 123} @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest json-producer-consumer
  (let [producer (jms/create-json-producer local-jms-server test-topic)
        was-run (utils/prepare-flag)
        received (atom nil)
        consume-fn (fn [obj] (reset! received obj) (utils/set-flag was-run))
        consumer (jms/create-json-consumer local-jms-server test-topic consume-fn)]
    (producer {:a "b"})
    (utils/await-flag was-run)
    (test/is (utils/flag-set? was-run))
    (test/is (= {"a" "b"} @received))
    (jms/close producer)
    (jms/close consumer)))

;(test/deftest json-producer-consumer-ratio
;  (let [producer (jms/create-json-producer local-jms-server test-topic)
;        was-run (utils/prepare-flag)
;        received (atom nil)
;        consume-fn (fn [obj] (reset! received obj) (utils/set-flag was-run))
;        consumer (jms/create-json-consumer local-jms-server test-topic consume-fn)]
;    (producer {:a 1/3})
;    (utils/await-flag was-run)
;    (test/is (utils/flag-set? was-run))
;    (test/is (= {"a" 1/3} @received))
;    (jms/close producer)
;    (jms/close consumer)))

(test/deftest json-producer-consumer-lzf
  (let [producer (jms/create-json-lzf-producer local-jms-server test-topic)
        was-run (utils/prepare-flag)
        received (atom nil)
        consume-fn (fn [obj] (reset! received obj) (utils/set-flag was-run))
        consumer (jms/create-json-lzf-consumer local-jms-server test-topic consume-fn)]
    (producer {:a "b"})
    (utils/await-flag was-run)
    (test/is (utils/flag-set? was-run))
    (test/is (= {"a" "b"} @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest json-producer-consumer-snappy
  (let [producer (jms/create-json-snappy-producer local-jms-server test-topic)
        was-run (utils/prepare-flag)
        received (atom nil)
        consume-fn (fn [obj] (reset! received obj) (utils/set-flag was-run))
        consumer (jms/create-json-snappy-consumer local-jms-server test-topic consume-fn)]
    (producer {:a "b"})
    (utils/await-flag was-run)
    (test/is (utils/flag-set? was-run))
    (test/is (= {"a" "b"} @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest json-producer-failsafe-json-consumer
  (let [producer (jms/create-json-producer local-jms-server test-topic)
        was-run (utils/prepare-flag)
        received (atom nil)
        consume-fn (fn [obj] (reset! received obj) (utils/set-flag was-run))
        consumer (jms/create-failsafe-json-consumer local-jms-server test-topic consume-fn)]
    (producer {"a" "b"})
    (utils/await-flag was-run)
    (test/is (utils/flag-set? was-run))
    (test/is (= {"a" "b"} @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest string-producer-failsafe-json-consumer
  (let [producer (jms/create-producer local-jms-server test-topic)
        was-run (utils/prepare-flag)
        received (atom nil)
        consume-fn (fn [obj] (reset! received obj) (utils/set-flag was-run))
        consumer (jms/create-failsafe-json-consumer local-jms-server test-topic consume-fn)]
    (producer "foo")
    (utils/await-flag was-run)
    (test/is (utils/flag-set? was-run))
    (test/is (= "foo" @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest object-producer-failsafe-json-consumer
  (let [producer (jms/create-producer local-jms-server test-topic)
        was-run (utils/prepare-flag)
        received (atom nil)
        consume-fn (fn [obj] (reset! received obj) (utils/set-flag was-run))
        consumer (jms/create-failsafe-json-consumer local-jms-server test-topic consume-fn)]
    (producer {"a" "b"})
    (utils/await-flag was-run)
    (test/is (utils/flag-set? was-run))
    (test/is (= "{\"a\" \"b\"}" @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest pooled-producer-normal-consumer
  (let [producer (jms/create-producer local-jms-server test-topic 3)
        was-run (utils/prepare-flag)
        received (ref nil)
        consume-fn (fn [obj] (dosync (ref-set received obj)) (utils/set-flag was-run))
        consumer (jms/create-consumer local-jms-server test-topic consume-fn)]
    (producer "a")
    (producer "b")
    (producer "c")
    (utils/await-flag was-run)
    (test/is (utils/flag-set? was-run))
    (test/is (= '("a" "b" "c") @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest pooled-producer-pooled-consumer
  (let [producer (jms/create-producer local-jms-server test-topic 3)
        was-run (utils/prepare-flag 3)
        received (ref [])
        consume-fn (fn [obj] (dosync (alter received conj obj)) (utils/set-flag was-run))
        consumer (jms/create-consumer local-jms-server test-topic consume-fn 3)]
    (producer "a")
    (producer "b")
    (producer "c")
    (utils/await-flag was-run)
    (test/is (utils/flag-set? was-run))
    (test/is (= ["a" "b" "c"] @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest pooled-producer-pooled-consumer-nippy
  (let [producer (jms/create-nippy-producer local-jms-server test-topic 3)
        was-run (utils/prepare-flag 3)
        received (ref [])
        consume-fn (fn [obj] (dosync (alter received conj obj)) (utils/set-flag was-run))
        consumer (jms/create-nippy-consumer local-jms-server test-topic consume-fn 3)]
    (producer "a")
    (producer "b")
    (producer "c")
    (utils/await-flag was-run)
    (test/is (utils/flag-set? was-run))
    (test/is (= ["a" "b" "c"] @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest pooled-producer-pooled-consumer-nippy-lz4
  (let [producer #_{:clj-kondo/ignore [:unresolved-var]}
                 (jms/create-nippy-producer
                   local-jms-server
                   test-topic
                   3
                   {:compressor nippy/lz4-compressor})
        was-run (utils/prepare-flag 3)
        received (ref [])
        consume-fn (fn [obj] (dosync (alter received conj obj)) (utils/set-flag was-run))
        consumer (jms/create-nippy-consumer local-jms-server test-topic consume-fn 3)]
    (producer "a")
    (producer "b")
    (producer "c")
    (utils/await-flag was-run)
    (test/is (utils/flag-set? was-run))
    (test/is (= ["a" "b" "c"] @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest pooled-producer-pooled-consumer-nippy-snappy
  (let [producer #_{:clj-kondo/ignore [:unresolved-var]}
                 (jms/create-nippy-producer
                   local-jms-server
                   test-topic
                   3
                   {:compressor nippy/snappy-compressor})
        was-run (utils/prepare-flag 3)
        received (ref [])
        consume-fn (fn [obj] (dosync (alter received conj obj)) (utils/set-flag was-run))
        consumer (jms/create-nippy-consumer local-jms-server test-topic consume-fn 3)]
    (producer "a")
    (producer "b")
    (producer "c")
    (utils/await-flag was-run)
    (test/is (utils/flag-set? was-run))
    (test/is (= ["a" "b" "c"] @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest pooled-producer-pooled-consumer-nippy-lzma2
  (let [producer #_{:clj-kondo/ignore [:unresolved-var]}
                 (jms/create-nippy-producer
                   local-jms-server
                   test-topic
                   3
                   {:compressor nippy/lzma2-compressor})
        was-run (utils/prepare-flag 3)
        received (ref [])
        consume-fn (fn [obj] (dosync (alter received conj obj)) (utils/set-flag was-run))
        consumer (jms/create-nippy-consumer local-jms-server test-topic consume-fn 3)]
    (producer "a")
    (producer "b")
    (producer "c")
    (utils/await-flag was-run)
    (test/is (utils/flag-set? was-run))
    (test/is (= ["a" "b" "c"] @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest pooled-producer-pooled-consumer-nippy-lzf
  (let [producer (jms/create-nippy-lzf-producer local-jms-server test-topic 3)
        was-run (utils/prepare-flag 3)
        received (ref [])
        consume-fn (fn [obj] (dosync (alter received conj obj)) (utils/set-flag was-run))
        consumer (jms/create-nippy-lzf-consumer local-jms-server test-topic consume-fn 3)]
    (producer "a")
    (producer "b")
    (producer "c")
    (utils/await-flag was-run)
    (test/is (utils/flag-set? was-run))
    (test/is (= ["a" "b" "c"] @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest pooled-producer-pooled-consumer-carbonite
  (let [producer (jms/create-carbonite-producer local-jms-server test-topic 3)
        was-run (utils/prepare-flag 3)
        received (ref [])
        consume-fn (fn [obj] (dosync (alter received conj obj)) (utils/set-flag was-run))
        consumer (jms/create-carbonite-consumer local-jms-server test-topic consume-fn 3)]
    (producer "a")
    (producer "b")
    (producer "c")
    (utils/await-flag was-run)
    (test/is (utils/flag-set? was-run))
    (test/is (= ["a" "b" "c"] @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest pooled-producer-pooled-consumer-carbonite-lzf
  (let [producer (jms/create-carbonite-lzf-producer local-jms-server test-topic 3)
        was-run (utils/prepare-flag 3)
        received (ref [])
        consume-fn (fn [obj] (dosync (alter received conj obj)) (utils/set-flag was-run))
        consumer (jms/create-carbonite-lzf-consumer local-jms-server test-topic consume-fn 3)]
    (producer "a")
    (producer "b")
    (producer "c")
    (utils/await-flag was-run)
    (test/is (utils/flag-set? was-run))
    (test/is (= ["a" "b" "c"] @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest pooled-producer-scheduled-autotransmit
  (let [producer (jms/create-producer local-jms-server test-topic 3)
        was-run (utils/prepare-flag 2)
        received (ref [])
        consume-fn (fn [obj] (dosync (alter received conj obj)) (utils/set-flag was-run))
        consumer (jms/create-consumer local-jms-server test-topic consume-fn 3)]
    (producer "a")
    (producer "b")
    (utils/await-flag was-run)
    (test/is (utils/flag-set? was-run))
    (test/is (= ["a" "b"] @received))
    (jms/close producer)
    (jms/close consumer)))
