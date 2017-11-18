;;;
;;;   Copyright 2016, Ruediger Gad
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Tests for JMS exchanging messages across multiple connectors."}  
  bowerick.test.multi-connector-exchange
  (:require
    [bowerick.jms :refer :all]
    [bowerick.test.test-helper :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.java.io :refer :all]
    [clojure.test :refer :all]))



(def urls ["tcp://127.0.0.1:55511"
           "stomp://127.0.0.1:55522"
;           "ws://127.0.0.1:55533"
           "mqtt://127.0.0.1:55544"])
(def test-topic "/topic/testtopic.foo")



(defn test-with-broker [t]
  (let [broker (start-test-broker urls)]
    (t)
    (stop broker)))

(use-fixtures :each test-with-broker)



(deftest test-string-exchange-json-producer-consumer
  (doseq [src urls dst urls]
    (println "Multi-connector string exchange test from" src "to" dst)
    (let [producer (create-json-producer src test-topic)
          received (atom nil)
          flag (prepare-flag)
          consume-fn (fn [obj] (reset! received obj) (set-flag flag))
          consumer (create-json-consumer dst test-topic consume-fn)]
      (producer "test-string")
      (await-flag flag)
      (is (= "test-string" @received))
      (close producer)
      (close consumer))))

(deftest test-simple-data-structure-exchange-json-producer-consumer
  (doseq [src urls dst urls]
    (println "Multi-connector simple-data-structure exchange test from" src "to" dst)
    (let [producer (create-json-producer src test-topic)
          data [1 2 :a :b "xyz" {:a "A" :b "B"} #{:c :d}]
          expected [1 2 "a" "b" "xyz" {"a" "A" "b" "B"} ["c" "d"]]
          received (atom nil)
          flag (prepare-flag)
          consume-fn (fn [obj] (reset! received obj) (set-flag flag))
          consumer (create-json-consumer dst test-topic consume-fn)]
      (producer data)
      (await-flag flag)
      (is (= expected @received))
      (close producer)
      (close consumer))))

;(deftest test-nippy-stress-data-exchange-json-producer-consumer
;  (doseq [src urls dst urls]
;    (println "Multi-connector nippy-stress-data exchange test from" src "to" dst)
;    (let [producer (create-json-producer src test-topic)
;          data (dissoc
;                 taoensso.nippy/stress-data-benchable
;                 :lazy-seq :lazy-seq-empty :sorted-map :sorted-set)
;          received (atom nil)
;          flag (prepare-flag)
;          consume-fn (fn [obj] (reset! received obj) (set-flag flag))
;          consumer (create-json-consumer dst test-topic consume-fn)]
;      (producer data)
;      (await-flag flag)
;      (is (not (identical? data @received)))
;      (is (= data @received))
;      (close producer)
;      (close consumer))))

