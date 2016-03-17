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
  clj-jms-activemq-toolkit.test.java-interop
  (:use clojure.test
        clj-assorted-utils.util
        clj-jms-activemq-toolkit.jms
        clj-jms-activemq-toolkit.test.jms-test-base)
  (:import (clj_jms_activemq_toolkit ActiveMqJmsController JmsConsumer JmsController JmsProducer)))

(use-fixtures :each single-test-fixture)

(deftest test-create-activemq-controller
  (let [controller (ActiveMqJmsController. *local-jms-server*)]
    (is (instance? JmsController controller))))

(deftest test-create-activemq-producer
  (let [controller (ActiveMqJmsController. *local-jms-server*)
        producer (.createProducer controller test-topic)]
    (is (instance? JmsProducer producer))))

(deftest test-create-activemq-producer
  (let [controller (ActiveMqJmsController. *local-jms-server*)
        producer (.createProducer controller test-topic)
        flag (prepare-flag)
        data (ref nil)
        consumer (proxy [JmsConsumer] []
                   (processObject [obj]
                     (dosync (ref-set data obj))
                     (set-flag flag)))
        _ (.connectConsumer controller test-topic ^JmsConsumer consumer)]
    (.sendObject producer "foo")
    (await-flag flag)
    (is (flag-set? flag))
    (is (= "foo" @data))))

(deftest test-start-stop-embedded-broker
  (let [controller (ActiveMqJmsController. "tcp://localhost:52525")]
    (.startEmbeddedBroker controller)
    (.stopEmbeddedBroker controller)))

(deftest test-start-stop-embedded-broker-with-data-exchange
  (let [controller (ActiveMqJmsController. "tcp://localhost:52525")
        _ (.startEmbeddedBroker controller)
        producer (.createProducer controller test-topic)
        flag (prepare-flag)
        data (ref nil)
        consumer (proxy [JmsConsumer] []
                   (processObject [obj]
                     (dosync (ref-set data obj))
                     (set-flag flag)))
        _ (.connectConsumer controller test-topic ^JmsConsumer consumer)]
    (.sendObject producer "foo")
    (await-flag flag)
    (is (flag-set? flag))
    (is (= "foo" @data))
    (.stopEmbeddedBroker controller)))


