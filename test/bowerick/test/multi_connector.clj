;;;
;;;   Copyright 2016, Ruediger Gad
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Tests for JMS interaction with a broker with multiple connectors."}  
  bowerick.test.multi-connector
  (:require
    [bowerick.jms :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.test :refer :all] clojure.test))



(def local-openwire-1 "tcp://127.0.0.1:44111")
(def local-openwire-2 "tcp://127.0.0.1:44222")
(def local-stomp-1 "stomp://127.0.0.1:44333")
(def local-stomp-2 "stomp://127.0.0.1:44444")
(def test-topic "/topic/testtopic.foo")



(deftest test-openwire-to-openwire
  (let [broker (start-broker [local-openwire-1 local-openwire-2])
        producer (create-producer local-openwire-1 test-topic)
        received (atom nil)
        flag (prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (set-flag flag))
        consumer (create-consumer local-openwire-2 test-topic consume-fn)]
    (producer "test-string")
    (await-flag flag)
    (is (= "test-string" @received))
    (close producer)
    (close consumer)
    (stop broker)))

(deftest test-stomp-to-stomp
  (let [broker (start-broker [local-stomp-1 local-stomp-2])
        producer (create-producer local-stomp-1 test-topic)
        received (atom nil)
        flag (prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (set-flag flag))
        consumer (create-consumer local-stomp-2 test-topic consume-fn)]
    (producer "test-string")
    (await-flag flag)
    (is (= "test-string" @received))
    (close producer)
    (close consumer)
    (stop broker)))

(deftest test-openwire-to-stomp
  (let [broker (start-broker [local-openwire-1 local-stomp-1])
        producer (create-producer local-openwire-1 test-topic)
        received (atom nil)
        flag (prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (set-flag flag))
        consumer (create-consumer local-stomp-1 test-topic consume-fn)]
    (producer "test-string")
    (await-flag flag)
    (is (= "test-string" @received))
    (close producer)
    (close consumer)
    (stop broker)))

