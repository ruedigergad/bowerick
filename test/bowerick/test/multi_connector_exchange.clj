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
    [bowerick.jms :as jms]
    [bowerick.test.test-helper :as th]
    [clj-assorted-utils.util :as utils]
    [clojure.test :as test]))



(def urls ["tcp://127.0.0.1:55511"
           "stomp://127.0.0.1:55522"
;           "ws://127.0.0.1:55533"
           "mqtt://127.0.0.1:55544"])
(def test-topic "/topic/testtopic.foo")



(defn test-with-broker [t]
  (let [broker (th/start-test-broker urls)]
    (t)
    (jms/stop broker)))

(test/use-fixtures :each test-with-broker)



(test/deftest test-string-exchange-json-producer-consumer
  (doseq [src urls dst urls]
    (println "Multi-connector string exchange test from" src "to" dst)
    (let [producer (jms/create-json-producer src test-topic)
          received (atom nil)
          flag (utils/prepare-flag)
          consume-fn (fn [obj] (reset! received obj) (utils/set-flag flag))
          consumer (jms/create-json-consumer dst test-topic consume-fn)]
      (producer "test-string")
      (utils/await-flag flag)
      (test/is (= "test-string" @received))
      (jms/close producer)
      (jms/close consumer))))

(test/deftest test-simple-data-structure-exchange-json-producer-consumer
  (doseq [src urls dst urls]
    (println "Multi-connector simple-data-structure exchange test from" src "to" dst)
    (let [producer (jms/create-json-producer src test-topic)
          data [1 2 :a :b "xyz" {:a "A" :b "B"} #{:c :d}]
          expected [1 2 "a" "b" "xyz" {"a" "A" "b" "B"} ["c" "d"]]
          received (atom nil)
          flag (utils/prepare-flag)
          consume-fn (fn [obj] (reset! received obj) (utils/set-flag flag))
          consumer (jms/create-json-consumer dst test-topic consume-fn)]
      (producer data)
      (utils/await-flag flag)
      (test/is (= expected @received))
      (jms/close producer)
      (jms/close consumer))))

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

