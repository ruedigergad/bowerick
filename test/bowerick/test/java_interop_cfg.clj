;;;
;;;   Copyright 2021, Ruediger Gad
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Tests for Java interop with configuration"}  
  bowerick.test.java-interop-cfg
  (:require
    [clojure.test :refer :all]
    [clj-assorted-utils.util :refer :all]
    [bowerick.jms :refer :all]
    [bowerick.test.test-helper :refer :all])
  (:import
    (bowerick JmsConsumerCallback JmsController JmsProducer)))



(def local-jms-server "ssl://127.0.0.1:42426")
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (binding [*trust-store-file* "test/cfg/blah_broker.ts"
                         *trust-store-password* "b4zZ0nK"
                         *key-store-file* "test/cfg/blah_broker.ks"
                         *key-store-password* "f00BAR"]
                 (start-test-broker local-jms-server))]
    (t)
    (stop broker)))

(use-fixtures :each test-with-broker)




(deftest test-producer-and-consumer
  (JmsController/configure "test/cfg/bowerick_test_config.cfg")
  (let [n 1
        producer (JmsController/createProducer local-jms-server test-topic n)
        flag (prepare-flag)
        data (ref nil)
        consumer-cb (proxy [JmsConsumerCallback] []
                      (processData [obj]
                        (dosync (ref-set data obj))
                        (set-flag flag)))
        consumer (JmsController/createConsumer local-jms-server test-topic ^JmsConsumerCallback consumer-cb n)]
    (.sendData producer "foo")
    (await-flag flag)
    (is (flag-set? flag))
    (is (= "foo" @data))
    (.close producer)
    (.close consumer)))

(deftest test-start-stop-embedded-broker-with-data-exchange
  (JmsController/configure "test/cfg/bowerick_test_config.cfg")
  (let [controller (JmsController. "ssl://localhost:52526")
        _ (.startEmbeddedBroker controller)
        n 1
        producer (JmsController/createProducer "ssl://localhost:52526" test-topic n)
        flag (prepare-flag)
        data (ref nil)
        consumer-cb (proxy [JmsConsumerCallback] []
                      (processData [obj]
                        (dosync (ref-set data obj))
                        (set-flag flag)))
        consumer (JmsController/createConsumer "ssl://localhost:52526" test-topic ^JmsConsumerCallback consumer-cb n)]
    (.sendData producer "foo")
    (await-flag flag)
    (is (flag-set? flag))
    (is (= "foo" @data))
    (.close producer)
    (.close consumer)
    (.stopEmbeddedBroker controller)))

