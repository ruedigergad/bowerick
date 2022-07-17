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
    [clojure.test :as test]
    [clj-assorted-utils.util :as utils]
    [bowerick.jms :as jms]
    [bowerick.test.test-helper :as th])
  (:import
    (bowerick JmsConsumerCallback JmsController JmsProducer)))



(def local-jms-server "tcp://127.0.0.1:52524")
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (th/start-test-broker local-jms-server)]
    (t)
    (jms/stop broker)))

(test/use-fixtures :each test-with-broker)



(test/deftest test-create-controller
  (let [controller (JmsController. local-jms-server)]
    (test/is (instance? JmsController controller))))

(test/deftest test-create-producer
  (let [producer (JmsController/createProducer local-jms-server test-topic 1)]
    (test/is (instance? JmsProducer producer))
    (.close producer)))

(test/deftest test-producer-and-consumer
  (let [n 1
        producer (JmsController/createProducer local-jms-server test-topic n)
        flag (utils/prepare-flag)
        data (ref nil)
        consumer-cb (proxy [JmsConsumerCallback] []
                      (processData [obj _]
                        (dosync (ref-set data obj))
                        (utils/set-flag flag)))
        consumer (JmsController/createConsumer local-jms-server test-topic ^JmsConsumerCallback consumer-cb n)]
    (.sendData producer "foo")
    (utils/await-flag flag)
    (test/is (utils/flag-set? flag))
    (test/is (= "foo" @data))
    (.close producer)
    (.close consumer)))

(test/deftest test-start-stop-embedded-broker
  (let [controller (JmsController. "tcp://localhost:52525")]
    (.startEmbeddedBroker controller)
    (.stopEmbeddedBroker controller)))

(test/deftest test-start-stop-embedded-broker-with-data-exchange
  (let [controller (JmsController. "tcp://localhost:52525")
        _ (.startEmbeddedBroker controller)
        n 1
        producer (JmsController/createProducer "tcp://localhost:52525" test-topic n)
        flag (utils/prepare-flag)
        data (ref nil)
        consumer-cb (proxy [JmsConsumerCallback] []
                      (processData [obj _]
                        (dosync (ref-set data obj))
                        (utils/set-flag flag)))
        consumer (JmsController/createConsumer "tcp://localhost:52525" test-topic ^JmsConsumerCallback consumer-cb n)]
    (.sendData producer "foo")
    (utils/await-flag flag)
    (test/is (utils/flag-set? flag))
    (test/is (= "foo" @data))
    (.close producer)
    (.close consumer)
    (.stopEmbeddedBroker controller)))

(test/deftest test-json-producer-and-consumer
  (let [n 1
        producer (JmsController/createJsonProducer local-jms-server test-topic n)
        flag (utils/prepare-flag)
        data (atom nil)
        consumer-cb (proxy [JmsConsumerCallback] []
                      (processData [obj _]
                        (reset! data obj)
                        (utils/set-flag flag)))
        consumer (JmsController/createJsonConsumer local-jms-server test-topic ^JmsConsumerCallback consumer-cb n)]
    (.sendData producer {"a" "A", "b" 123})
    (utils/await-flag flag)
    (test/is (utils/flag-set? flag))
    (test/is (= {"a" "A", "b" 123} @data))
    (.close producer)
    (.close consumer)))

(test/deftest test-pooled-producer-and-consumer
  (let [n 3
        cntr (utils/counter)
        producer (JmsController/createProducer local-jms-server test-topic n)
        flag (utils/prepare-flag n)
        data (atom "")
        consumer-cb (proxy [JmsConsumerCallback] []
                      (processData [obj _]
                        (swap! data str obj)
                        (cntr inc)
                        (utils/set-flag flag)))
        consumer (JmsController/createConsumer local-jms-server test-topic ^JmsConsumerCallback consumer-cb n)]
    (.sendData producer "foo")
    (.sendData producer "bar")
    (.sendData producer "baz")
    (utils/await-flag flag)
    (test/is (utils/flag-set? flag))
    (test/is (= n (cntr)))
    (test/is (= "foobarbaz" @data))
    (.close producer)
    (.close consumer)))

(test/deftest test-pooled-carbonite-producer-and-consumer
  (let [n 3
        cntr (utils/counter)
        producer (JmsController/createCarboniteProducer local-jms-server test-topic n)
        flag (utils/prepare-flag n)
        data (atom "")
        consumer-cb (proxy [JmsConsumerCallback] []
                      (processData [obj _]
                        (swap! data str obj)
                        (cntr inc)
                        (utils/set-flag flag)))
        consumer (JmsController/createCarboniteConsumer local-jms-server test-topic ^JmsConsumerCallback consumer-cb n)]
    (.sendData producer "foo")
    (.sendData producer "bar")
    (.sendData producer "baz")
    (utils/await-flag flag)
    (test/is (utils/flag-set? flag))
    (test/is (= n (cntr)))
    (test/is (= "foobarbaz" @data))
    (.close producer)
    (.close consumer)))

(test/deftest test-pooled-carbonite-lzf-producer-and-consumer
  (let [n 3
        cntr (utils/counter)
        producer (JmsController/createCarboniteLzfProducer local-jms-server test-topic n)
        flag (utils/prepare-flag n)
        data (atom "")
        consumer-cb (proxy [JmsConsumerCallback] []
                      (processData [obj _]
                        (swap! data str obj)
                        (cntr inc)
                        (utils/set-flag flag)))
        consumer (JmsController/createCarboniteLzfConsumer local-jms-server test-topic ^JmsConsumerCallback consumer-cb n)]
    (.sendData producer "foo")
    (.sendData producer "bar")
    (.sendData producer "baz")
    (utils/await-flag flag)
    (test/is (utils/flag-set? flag))
    (test/is (= n (cntr)))
    (test/is (= "foobarbaz" @data))
    (.close producer)
    (.close consumer)))

