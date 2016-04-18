;;;
;;;   Copyright 2016, Ruediger Gad
;;;   Copyright 2014, 2015 University of Applied Sciences Frankfurt am Main
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Tests for Java interop"}  
  bowerick.test.java-interop
  (:require
    [clojure.test :refer :all]
    [clj-assorted-utils.util :refer :all]
    [bowerick.jms :refer :all]
    [bowerick.test.jms-test-base :refer :all])
  (:import
    (bowerick JmsConsumerCallback JmsController JmsProducer)))

(use-fixtures :each single-test-fixture)

(deftest test-create-activemq-controller
  (let [controller (JmsController. *local-jms-server*)]
    (is (instance? JmsController controller))))

(deftest test-create-activemq-producer
  (let [controller (JmsController. *local-jms-server*)
        producer (.createProducer controller test-topic)]
    (is (instance? JmsProducer producer))
    (.close producer)))

(deftest test-create-activemq-producer-and-consumer
  (let [controller (JmsController. *local-jms-server*)
        producer (.createProducer controller test-topic)
        flag (prepare-flag)
        data (ref nil)
        consumer-cb (proxy [JmsConsumerCallback] []
                      (processData [obj]
                        (dosync (ref-set data obj))
                        (set-flag flag)))
        consumer (.createConsumer controller test-topic ^JmsConsumerCallback consumer-cb)]
    (.sendData producer "foo")
    (await-flag flag)
    (is (flag-set? flag))
    (is (= "foo" @data))
    (.close producer)
    (.close consumer)))

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
        consumer-cb (proxy [JmsConsumerCallback] []
                      (processData [obj]
                        (dosync (ref-set data obj))
                        (set-flag flag)))
        consumer (.createConsumer controller test-topic ^JmsConsumerCallback consumer-cb)]
    (.sendData producer "foo")
    (await-flag flag)
    (is (flag-set? flag))
    (is (= "foo" @data))
    (.close producer)
    (.close consumer)
    (.stopEmbeddedBroker controller)))

