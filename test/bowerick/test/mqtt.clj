;;;
;;;   Copyright 2016, Ruediger Gad
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Tests for JMS Interaction via MQTT"}  
  bowerick.test.mqtt
  (:require
    [bowerick.jms :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.test :refer :all] clojure.test))



(def url-openwire "tcp://127.0.0.1:42423")
(def url-stomp "stomp://127.0.0.1:42424")
(def url-mqtt "mqtt://127.0.0.1:1883")
(def url-mqtt-ssl "mqtt+ssl://127.0.0.1:1884")
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (binding [*trust-store-file* "test/ssl/broker.ts"
                         *trust-store-password* "password"
                         *key-store-file* "test/ssl/broker.ks"
                         *key-store-password* "password"]
                 (start-broker [url-openwire url-stomp url-mqtt (str url-mqtt-ssl "?needClientAuth=true")]))]
    (t)
    (stop broker)))

(use-fixtures :each test-with-broker)



(deftest mqtt-string-test
  (let [producer (create-producer url-mqtt test-topic)
        received (atom nil)
        flag (prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (set-flag flag))
        consumer (create-consumer url-mqtt test-topic consume-fn)]
    (producer "¿Qué pasa?")
    (await-flag flag)
    (is (instance? byte-array-type @received))
    (is (= "¿Qué pasa?" (String. @received)))
    (close producer)
    (close consumer)))

(deftest mqtt-to-openwire-string-test
  (let [producer (create-producer url-mqtt test-topic)
        received (atom nil)
        flag (prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (set-flag flag))
        consumer (create-consumer url-openwire test-topic consume-fn)]
    (producer "¿Qué pasa?")
    (await-flag flag)
    (is (instance? byte-array-type @received))
    (is (= "¿Qué pasa?" (String. @received)))
    (close producer)
    (close consumer)))

(deftest mqtt-to-stomp-string-test
  (let [producer (create-producer url-mqtt test-topic)
        received (atom nil)
        flag (prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (set-flag flag))
        consumer (create-consumer url-stomp test-topic consume-fn)]
    (producer "¿Cómo estás?")
    (await-flag flag)
    (is (instance? byte-array-type @received))
    (is (= "¿Cómo estás?" (String. @received)))
    (close producer)
    (close consumer)))

(deftest mqtt-ssl-to-stomp-string-test
  (let [producer (binding [*trust-store-file* "test/ssl/client.ts"
                           *trust-store-password* "password"
                           *key-store-file* "test/ssl/client.ks"
                           *key-store-password* "password"]
                   (create-producer url-mqtt-ssl test-topic))
        received (atom nil)
        flag (prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (set-flag flag))
        consumer (create-consumer url-stomp test-topic consume-fn)]
    (producer "¿Cómo estás?")
    (await-flag flag)
    (is (instance? byte-array-type @received))
    (is (= "¿Cómo estás?" (String. @received)))
    (close producer)
    (close consumer)))

