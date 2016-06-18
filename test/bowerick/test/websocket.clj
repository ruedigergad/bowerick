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
    [clj-assorted-utils.util :refer :all]
    [clojure.test :refer :all] clojure.test))



(def url-openwire "tcp://127.0.0.1:42423")
(def url-stomp "stomp://127.0.0.1:42424")
(def url-websocket "ws://127.0.0.1:42425")
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (start-broker [url-openwire url-stomp url-websocket])]
    (t)
    (stop broker)))

(use-fixtures :each test-with-broker)



(deftest websocket-to-openwire-string-test
  (let [producer (create-producer url-websocket test-topic)
        received (atom nil)
        flag (prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (set-flag flag))
        consumer (create-single-consumer url-openwire test-topic consume-fn)]
    (producer "¿Qué pasa?")
    (await-flag flag)
    (is (instance? byte-array-type @received))
    (is (= "¿Qué pasa?" (String. @received)))
    (close producer)
    (close consumer)))

(deftest websocket-to-stomp-string-test
  (let [producer (create-producer url-websocket test-topic)
        received (atom nil)
        flag (prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (set-flag flag))
        consumer (create-single-consumer url-stomp test-topic consume-fn)]
    (producer "¡No pasa nada!")
    (await-flag flag)
    (is (instance? byte-array-type @received))
    (is (= "¡No pasa nada!" (String. @received)))
    (close producer)
    (close consumer)))

