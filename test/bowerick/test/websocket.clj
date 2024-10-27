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
    [bowerick.jms :as jms]
    [bowerick.test.test-helper :as th]
    [clj-assorted-utils.util :as utils]
    [clojure.test :as test]))



(def url-openwire "tcp://127.0.0.1:33323")
(def url-stomp "stomp://127.0.0.1:33324")
(def url-websocket "ws://127.0.0.1:33325")
(def url-websocket-ssl "wss://127.0.0.1:33326/?needClientAuth=true")
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (binding [jms/*trust-store-file* "test/ssl/broker.ts"
                         jms/*trust-store-password* "password"
                         jms/*key-store-file* "test/ssl/broker.ks"
                         jms/*key-store-password* "password"]
                 (th/start-test-broker [url-openwire url-stomp url-websocket url-websocket-ssl]))]
    (t)
    (jms/stop broker)))

(test/use-fixtures :each test-with-broker)



(test/deftest websocket-string-test
  (let [received (atom nil)
        flag (utils/prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (utils/set-flag flag))
        consumer (jms/create-consumer url-websocket test-topic consume-fn)
        producer (jms/create-producer url-websocket test-topic)]
    (producer "¿Qué pasa?")
    (utils/await-flag flag)
    (test/is (instance? utils/byte-array-type @received))
    (test/is (= "¿Qué pasa?" (String. @received)))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest websocket-to-openwire-string-test
  (let [received (atom nil)
        flag (utils/prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (utils/set-flag flag))
        consumer (jms/create-consumer url-openwire test-topic consume-fn)
        producer (jms/create-producer url-websocket test-topic)]
    (producer "¿Qué pasa?")
    (utils/await-flag flag)
    (test/is (instance? utils/byte-array-type @received))
    (test/is (= "¿Qué pasa?" (String. @received)))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest websocket-to-stomp-string-test
  (let [received (atom nil)
        flag (utils/prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (utils/set-flag flag))
        consumer (jms/create-consumer url-stomp test-topic consume-fn)
        producer (jms/create-producer url-websocket test-topic)]
    (producer "¿Cómo estás?")
    (utils/await-flag flag)
    (test/is (instance? utils/byte-array-type @received))
    (test/is (= "¿Cómo estás?" (String. @received)))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest websocket-ssl-to-stomp-string-test
  (let [received (atom nil)
        flag (utils/prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (utils/set-flag flag))
        consumer (jms/create-consumer url-stomp test-topic consume-fn)
        producer (binding [jms/*trust-store-file* "test/ssl/client.ts"
                           jms/*trust-store-password* "password"
                           jms/*key-store-file* "test/ssl/client.ks"
                           jms/*key-store-password* "password"]
                   (jms/create-producer url-websocket-ssl test-topic))]
    (producer "¿Cómo estás?")
    (utils/await-flag flag)
    (test/is (instance? utils/byte-array-type @received))
    (test/is (= "¿Cómo estás?" (String. @received)))
    (jms/close producer)
    (jms/close consumer)))

;(test/deftest openwire-to-websocket-string-test
;  (let [producer (jms/create-producer url-openwire test-topic)
;        received (atom nil)
;        flag (utils/prepare-flag)
;        consume-fn (fn [obj] (reset! received obj) (utils/set-flag flag))
;        consumer (jms/create-consumer url-websocket test-topic consume-fn)]
;    (producer "¿Qué tal?")
;    (utils/await-flag flag)
;    (test/is (instance? utils/byte-array-type @received))
;    (test/is (= "¿Qué tal?" (String. @received)))
;    (jms/close producer)
;    (jms/close consumer)))
