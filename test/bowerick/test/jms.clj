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
    [bowerick.test.jms-test-base :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.test :refer :all] clojure.test))

(use-fixtures :each single-test-fixture)

(deftest test-create-topic
  (let [producer (create-producer *local-jms-server* test-topic)]
    (is (not (nil? producer)))
    (close producer)))

(deftest producer-consumer
  (let [producer (create-producer *local-jms-server* test-topic)
        was-run (prepare-flag)
        consume-fn (fn [_] (set-flag was-run))
        consumer (create-consumer *local-jms-server* test-topic consume-fn)]
    (is (not (nil? producer)))
    (is (not (nil? consumer)))
    (producer "¡Hola!")
    (await-flag was-run)
    (is (flag-set? was-run))
    (close producer)
    (close consumer)))

(deftest send-list
  (let [producer (create-producer *local-jms-server* test-topic)
        received (ref nil)
        flag (prepare-flag)
        consume-fn (fn [obj] (dosync (ref-set received obj)) (set-flag flag))
        consumer (create-consumer *local-jms-server* test-topic consume-fn)
        data '(:a :b :c)]
    (is (not= data @received))
    (producer data)
    (await-flag flag)
    (is (= data @received))
    (close producer)
    (close consumer)))

(deftest pooled-producer-normal-consumer
  (let [producer (create-pooled-producer *local-jms-server* test-topic 3)
        was-run (prepare-flag)
        received (ref nil)
        consume-fn (fn [obj] (dosync (ref-set received obj)) (set-flag was-run))
        consumer (create-consumer *local-jms-server* test-topic consume-fn)]
    (producer "a")
    (producer "b")
    (producer "c")
    (await-flag was-run)
    (is (flag-set? was-run))
    (is (= '("a" "b" "c") @received))
    (close producer)
    (close consumer)))

