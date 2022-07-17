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
    [bowerick.jms :as jms]
    [bowerick.test.test-helper :as th]
    [clj-assorted-utils.util :as utils]
    [clojure.test :as test]))



(def local-openwire-1 "tcp://127.0.0.1:44111")
(def local-openwire-2 "tcp://127.0.0.1:44222")
(def local-stomp-1 "stomp://127.0.0.1:44333")
(def local-stomp-2 "stomp://127.0.0.1:44444")
(def test-topic "/topic/testtopic.foo")



(test/deftest test-openwire-to-openwire
  (let [broker (th/start-test-broker [local-openwire-1 local-openwire-2])
        producer (jms/create-producer local-openwire-1 test-topic)
        received (atom nil)
        flag (utils/prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (utils/set-flag flag))
        consumer (jms/create-consumer local-openwire-2 test-topic consume-fn)]
    (producer "test-string")
    (utils/await-flag flag)
    (test/is (= "test-string" @received))
    (jms/close producer)
    (jms/close consumer)
    (jms/stop broker)))

(test/deftest test-stomp-to-stomp
  (let [broker (th/start-test-broker [local-stomp-1 local-stomp-2])
        producer (jms/create-producer local-stomp-1 test-topic)
        received (atom nil)
        flag (utils/prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (utils/set-flag flag))
        consumer (jms/create-consumer local-stomp-2 test-topic consume-fn)]
    (producer "test-string")
    (utils/await-flag flag)
    (test/is (= "test-string" @received))
    (jms/close producer)
    (jms/close consumer)
    (jms/stop broker)))

(test/deftest test-openwire-to-stomp
  (let [broker (th/start-test-broker [local-openwire-1 local-stomp-1])
        producer (jms/create-producer local-openwire-1 test-topic)
        received (atom nil)
        flag (utils/prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (utils/set-flag flag))
        consumer (jms/create-consumer local-stomp-1 test-topic consume-fn)]
    (producer "test-string")
    (utils/await-flag flag)
    (test/is (= String (type @received)))
    (test/is (= "test-string" @received))
    (jms/close producer)
    (jms/close consumer)
    (jms/stop broker)))

(test/deftest test-stomp-to-openwire
  (let [broker (th/start-test-broker [local-openwire-1 local-stomp-1])
        producer (jms/create-producer local-stomp-1 test-topic)
        received (atom nil)
        flag (utils/prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (utils/set-flag flag))
        consumer (jms/create-consumer local-openwire-1 test-topic consume-fn)]
    (producer "test-string")
    (utils/await-flag flag)
    (test/is (= String (type @received)))
    (test/is (= "test-string" @received))
    (jms/close producer)
    (jms/close consumer)
    (jms/stop broker)))

(test/deftest test-stomp-to-openwire-byte-array
  (let [broker (th/start-test-broker [local-openwire-1 local-stomp-1])
        producer (jms/create-producer local-stomp-1 test-topic)
        received (atom nil)
        data (byte-array (map byte [1 2 3 4 5]))
        flag (utils/prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (utils/set-flag flag))
        consumer (jms/create-consumer local-openwire-1 test-topic consume-fn)]
    (producer data)
    (utils/await-flag flag)
    (doall (map (fn [a b] (test/is (= a b))) data @received))
    (jms/close producer)
    (jms/close consumer)
    (jms/stop broker)))

(test/deftest test-openwire-to-stomp-byte-array
  (let [broker (th/start-test-broker [local-openwire-1 local-stomp-1])
        producer (jms/create-producer local-openwire-1 test-topic)
        received (atom nil)
        data (byte-array (map byte [1 2 3 4 5]))
        flag (utils/prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (utils/set-flag flag))
        consumer (jms/create-consumer local-stomp-1 test-topic consume-fn)]
    (producer data)
    (utils/await-flag flag)
    (doall (map (fn [a b] (test/is (= a b))) data @received))
    (jms/close producer)
    (jms/close consumer)
    (jms/stop broker)))

(test/deftest test-stomp-to-openwire-nippy
  (let [broker (th/start-test-broker [local-openwire-1 local-stomp-1])
        producer (jms/create-nippy-producer local-stomp-1 test-topic)
        received (atom nil)
        flag (utils/prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (utils/set-flag flag))
        consumer (jms/create-nippy-consumer local-openwire-1 test-topic consume-fn)]
    (producer "test-string")
    (utils/await-flag flag)
    (test/is (= "test-string" @received))
    (jms/close producer)
    (jms/close consumer)
    (jms/stop broker)))

(test/deftest test-stomp-to-openwire-nippy-complexer-data
  (let [broker (th/start-test-broker [local-openwire-1 local-stomp-1])
        producer (jms/create-nippy-producer local-stomp-1 test-topic)
        received (atom nil)
        flag (utils/prepare-flag)
        consume-fn (fn [obj] (reset! received obj) (utils/set-flag flag))
        consumer (jms/create-nippy-consumer local-openwire-1 test-topic consume-fn)]
    (producer [1 2 :a :b {:c "CDE"}])
    (utils/await-flag flag)
    (test/is (= [1 2 :a :b {:c "CDE"}] @received))
    (jms/close producer)
    (jms/close consumer)
    (jms/stop broker)))

