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
  (:use clojure.test
        clj-assorted-utils.util
        bowerick.jms
        bowerick.test.jms-test-base))

(use-fixtures :each single-test-fixture)

(deftest test-fixture
  (is true))

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
        received (ref '())    
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

