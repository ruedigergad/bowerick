;;;
;;;   Copyright 2017, Ruediger Gad
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Tests for advanced message handling features."}  
  bowerick.test.advanced-message-handling
  (:require
    [bowerick.jms :as jms]
    [bowerick.test.test-helper :as th]
    [clj-assorted-utils.util :as utils]
    [clojure.test :as test]))



(def local-openwire "tcp://127.0.0.1:33111")
(def local-stomp "stomp://127.0.0.1:33222")
(def local-mqtt "mqtt://127.0.0.1:33333")
(def local-ws "ws://127.0.0.1:33444")
(def test-topic "/topic/testtopic.foo")



(test/deftest get-ws-stomp-headers-test
  (System/setProperty "java.net.preferIPv4Stack" "true")
  (let [broker (th/start-test-broker [local-ws])
        received-data (atom nil)
        received-headers (atom nil)
        flag (utils/prepare-flag)
        consume-fn (fn [data hdrs]
                     (reset! received-data data)
                     (reset! received-headers hdrs)
                     (utils/set-flag flag))
        consumer (jms/create-failsafe-json-consumer local-ws test-topic consume-fn)
        producer (jms/create-json-producer local-ws test-topic)]
    (println "Sending test-string...")
    (producer "test-string")
    (utils/await-flag flag)
    (println "Received message. Comparing result...")
    (test/is (= "test-string" @received-data))
    (test/is (instance? org.springframework.messaging.simp.stomp.StompHeaders @received-headers))
    (test/is (= test-topic (.getDestination @received-headers)))
    (test/is (= 13 (.getContentLength @received-headers)))
    (println "RECEIVED HEADERS (WS/STOMP):" (str @received-headers))
    (jms/close producer)
    (jms/close consumer)
    (jms/stop broker)))

(test/deftest get-mqtt-message-test
  (let [broker (th/start-test-broker [local-mqtt])
        producer (jms/create-json-producer local-mqtt test-topic)
        received-data (atom nil)
        received-msg (atom nil)
        flag (utils/prepare-flag)
        consume-fn (fn [data msg]
                     (reset! received-data data)
                     (reset! received-msg msg)
                     (utils/set-flag flag))
        consumer (jms/create-failsafe-json-consumer local-mqtt test-topic consume-fn)]
    (producer "test-string")
    (utils/await-flag flag)
    (test/is (= "test-string" @received-data))
    (test/is (instance? org.eclipse.paho.client.mqttv3.MqttMessage @received-msg))
    (test/is (= 1 (.getQos @received-msg)))
    (test/is (= 1 (.getId @received-msg)))
    (test/is (= "\"test-string\"" (String. (.getPayload @received-msg))))
    (jms/close producer)
    (jms/close consumer)
    (jms/stop broker)))

(test/deftest get-openwire-message-test
  (let [broker (th/start-test-broker [local-openwire])
        producer (jms/create-json-producer local-openwire test-topic)
        received-data (atom nil)
        received-msg (atom nil)
        flag (utils/prepare-flag)
        consume-fn (fn [data msg]
                     (reset! received-data data)
                     (reset! received-msg msg)
                     (utils/set-flag flag))
        consumer (jms/create-failsafe-json-consumer local-openwire test-topic consume-fn)]
    (producer "test-string")
    (utils/await-flag flag)
    (test/is (= "test-string" @received-data))
    (test/is (instance? jakarta.jms.Message @received-msg))
    (println "RECEIVED MESSAGE (OPENWIRE):" (str @received-msg))
    (jms/close producer)
    (jms/close consumer)
    (jms/stop broker)))

(test/deftest get-stomp-message-test
  (let [broker (th/start-test-broker [local-stomp])
        producer (jms/create-json-producer local-stomp test-topic)
        received-data (atom nil)
        received-msg (atom nil)
        flag (utils/prepare-flag)
        consume-fn (fn [data msg]
                     (reset! received-data data)
                     (reset! received-msg msg)
                     (utils/set-flag flag))
        consumer (jms/create-failsafe-json-consumer local-stomp test-topic consume-fn)]
    (producer "test-string")
    (utils/await-flag flag)
    (test/is (= "test-string" @received-data))
    (test/is (instance? jakarta.jms.Message @received-msg))
    (println "RECEIVED MESSAGE (STOMP):" (str @received-msg))
    (jms/close producer)
    (jms/close consumer)
    (jms/stop broker)))

(test/deftest openwire-custom-message-properties-test
  (let [broker (th/start-test-broker [local-openwire])
        producer (jms/create-json-producer local-openwire test-topic)
        received-data (atom nil)
        received-msg (atom nil)
        flag (utils/prepare-flag)
        message-properties {"Some Boolean" true
                            "Some Byte" (byte 42)
                            "Some Double" 1.701
                            "Some Float" (float 1.701)
                            "Some Integer" (int 1701)
                            "Some Long" 1701
                            "Some Short" (short 1701)
                            "Some String" "Enterprise"}
        consume-fn (fn [data msg]
                     (reset! received-data data)
                     (reset! received-msg msg)
                     (utils/set-flag flag))
        consumer (jms/create-failsafe-json-consumer local-openwire test-topic consume-fn)]
    (producer "test-string" {jms/msg-prop-key message-properties})
    (utils/await-flag flag)
    (test/is (.getBooleanProperty @received-msg "Some Boolean"))
    (test/is (= (byte 42) (.getByteProperty @received-msg "Some Byte")))
    (test/is (= 1.701 (.getDoubleProperty @received-msg "Some Double")))
    (test/is (= (float 1.701) (.getFloatProperty @received-msg "Some Float")))
    (test/is (= (int 1701) (.getIntProperty @received-msg "Some Integer")))
    (test/is (= 1701 (.getLongProperty @received-msg "Some Long")))
    (test/is (= (short 1701) (.getShortProperty @received-msg "Some Short")))
    (test/is (= "Enterprise" (.getStringProperty @received-msg "Some String")))
    (jms/close producer)
    (jms/close consumer)
    (jms/stop broker)))

(test/deftest stomp-custom-message-properties-test
  (let [broker (th/start-test-broker [local-stomp])
        producer (jms/create-json-producer local-stomp test-topic)
        received-data (atom nil)
        received-msg (atom nil)
        flag (utils/prepare-flag)
        message-properties {"Some Boolean" false
                            "Some Byte" (byte 21)
                            "Some Double" 1.864
                            "Some Float" (float 1.864)
                            "Some Integer" (int 1864)
                            "Some Long" 1864
                            "Some Short" (short 1864)
                            "Some String" "Reliant"}
        consume-fn (fn [data msg]
                     (reset! received-data data)
                     (reset! received-msg msg)
                     (utils/set-flag flag))
        consumer (jms/create-failsafe-json-consumer local-stomp test-topic consume-fn)]
    (producer "test-string" {jms/msg-prop-key message-properties})
    (utils/await-flag flag)
    (test/is (not (.getBooleanProperty @received-msg "Some Boolean")))
    (test/is (= (byte 21) (.getByteProperty @received-msg "Some Byte")))
    (test/is (= 1.864 (.getDoubleProperty @received-msg "Some Double")))
    (test/is (= (float 1.864) (.getFloatProperty @received-msg "Some Float")))
    (test/is (= (int 1864) (.getIntProperty @received-msg "Some Integer")))
    (test/is (= 1864 (.getLongProperty @received-msg "Some Long")))
    (test/is (= (short 1864) (.getShortProperty @received-msg "Some Short")))
    (test/is (= "Reliant" (.getStringProperty @received-msg "Some String")))
    (jms/close producer)
    (jms/close consumer)
    (jms/stop broker)))
