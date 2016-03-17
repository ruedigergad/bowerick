;;;
;;;   Copyright 2014, University of Applied Sciences Frankfurt am Main
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Tests for Java interop"}  
  bowerick.test.java-interop
  (:use clojure.test
        clj-assorted-utils.util
        bowerick.jms
        bowerick.test.jms-test-base)
  (:import (bowerick JmsController JmsConsumerCallback JmsController JmsProducer)))

(use-fixtures :each single-test-fixture)

(deftest test-create-activemq-controller
  (let [controller (JmsController. *local-jms-server*)]
    (is (instance? JmsController controller))))

(deftest test-create-activemq-producer
  (let [controller (JmsController. *local-jms-server*)
        producer (.createProducer controller test-topic)]
    (is (instance? JmsProducer producer))
    (.close producer)))

(deftest test-create-activemq-producer
  (let [controller (JmsController. *local-jms-server*)
        producer (.createProducer controller test-topic)
        flag (prepare-flag)
        data (ref nil)
        consumer (proxy [JmsConsumerCallback] []
                   (processObject [obj]
                     (dosync (ref-set data obj))
                     (set-flag flag)))
        _ (.connectConsumer controller test-topic ^JmsConsumerCallback consumer)]
    (.sendObject producer "foo")
    (await-flag flag)
    (is (flag-set? flag))
    (is (= "foo" @data))
    (.close producer)))

(deftest test-start-stop-embedded-broker
  (let [controller (JmsController. "tcp://localhost:52525")]
    (.startEmbeddedBroker controller)
    (.stopEmbeddedBroker controller)))

(deftest test-start-stop-embedded-broker-with-data-exchange
  (let [controller (JmsController. "tcp://localhost:52525")
        _ (.startEmbeddedBroker controller)
        producer (.createProducer controller test-topic)
        flag (prepare-flag)
        data (ref nil)
        consumer (proxy [JmsConsumerCallback] []
                   (processObject [obj]
                     (dosync (ref-set data obj))
                     (set-flag flag)))
        _ (.connectConsumer controller test-topic ^JmsConsumerCallback consumer)]
    (.sendObject producer "foo")
    (await-flag flag)
    (is (flag-set? flag))
    (is (= "foo" @data))
    (.close producer)
    (.stopEmbeddedBroker controller)))


