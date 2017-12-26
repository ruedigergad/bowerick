;;;
;;;   Copyright 2016, Ruediger Gad
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Tests for JMS Interaction via Websockets"}  
  bowerick.test.websocket
  (:require
    [bowerick.jms :refer :all]
    [bowerick.test.test-helper :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.test :refer :all]))



(def url-openwire "tcp://127.0.0.1:42423")
(def url-stomp "stomp://127.0.0.1:42424")
(def url-websocket "ws://127.0.0.1:42425")
(def url-websocket-ssl "wss://127.0.0.1:42426?needClientAuth=true")
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (binding [*trust-store-file* "test/ssl/broker.ts"
                         *trust-store-password* "password"
                         *key-store-file* "test/ssl/broker.ks"
                         *key-store-password* "password"]
                 (start-test-broker [url-openwire url-stomp url-websocket url-websocket-ssl]))]
    (t)
    (stop broker)))

(use-fixtures :each test-with-broker)



(deftest websocket-string-test
  (let [received (atom nil)
        flag (prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (set-flag flag))
        consumer (create-consumer url-websocket test-topic consume-fn)
        producer (create-producer url-websocket test-topic)]
    (producer "¿Qué pasa?")
    (await-flag flag)
    (is (instance? byte-array-type @received))
    (is (= "¿Qué pasa?" (String. @received)))
    (close producer)
    (close consumer)))

(deftest websocket-to-openwire-string-test
  (let [received (atom nil)
        flag (prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (set-flag flag))
        consumer (create-consumer url-openwire test-topic consume-fn)
        producer (create-producer url-websocket test-topic)]
    (producer "¿Qué pasa?")
    (await-flag flag)
    (is (instance? byte-array-type @received))
    (is (= "¿Qué pasa?" (String. @received)))
    (close producer)
    (close consumer)))

(deftest websocket-to-stomp-string-test
  (let [received (atom nil)
        flag (prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (set-flag flag))
        consumer (create-consumer url-stomp test-topic consume-fn)
        producer (create-producer url-websocket test-topic)]
    (producer "¿Cómo estás?")
    (await-flag flag)
    (is (instance? byte-array-type @received))
    (is (= "¿Cómo estás?" (String. @received)))
    (close producer)
    (close consumer)))

(deftest websocket-ssl-to-stomp-string-test
  (let [received (atom nil)
        flag (prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (set-flag flag))
        consumer (create-consumer url-stomp test-topic consume-fn)
        producer (binding [*trust-store-file* "test/ssl/client.ts"
                           *trust-store-password* "password"
                           *key-store-file* "test/ssl/client.ks"
                           *key-store-password* "password"]
                   (create-producer url-websocket-ssl test-topic))]
    (producer "¿Cómo estás?")
    (await-flag flag)
    (is (instance? byte-array-type @received))
    (is (= "¿Cómo estás?" (String. @received)))
    (close producer)
    (close consumer)))

;(deftest openwire-to-websocket-string-test
;  (let [producer (create-producer url-openwire test-topic)
;        received (atom nil)
;        flag (prepare-flag)
;        consume-fn (fn [obj] (reset! received obj) (set-flag flag))
;        consumer (create-consumer url-websocket test-topic consume-fn)]
;    (producer "¿Qué tal?")
;    (await-flag flag)
;    (is (instance? byte-array-type @received))
;    (is (= "¿Qué tal?" (String. @received)))
;    (close producer)
;    (close consumer)))

