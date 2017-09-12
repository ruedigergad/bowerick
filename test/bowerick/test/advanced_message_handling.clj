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
    [bowerick.jms :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.java.io :refer :all]
    [clojure.test :refer :all]))



(def local-openwire "tcp://127.0.0.1:33111")
(def local-stomp "stomp://127.0.0.1:33222")
(def local-mqtt "mqtt://127.0.0.1:33333")
(def local-ws "ws://127.0.0.1:33333")
(def test-topic "/topic/testtopic.foo")



(deftest get-ws-stomp-headers-test
  (let [broker (start-broker [local-ws])
        producer (create-producer local-ws test-topic)
        received-data (atom nil)
        received-headers (atom nil)
        flag (prepare-flag)
        consume-fn (fn [data hdrs]
                     (reset! received-data data)
                     (reset! received-headers hdrs)
                     (set-flag flag))
        consumer (create-consumer local-ws test-topic consume-fn)]
    (producer "test-string")
    (await-flag flag)
    (is (= "test-string" (String. @received-data)))
    (is (= test-topic (.getDestination @received-headers)))
    (is (= 11 (.getContentLength @received-headers)))
    (println "RECEIVED HEADERS:" (str @received-headers))
    (close producer)
    (close consumer)
    (stop broker)))

