;;;
;;;   Copyright 2016, Ruediger Gad
;;;   Copyright 2014, 2015, University of Applied Sciences Frankfurt am Main
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Tests for different JMS transport connections"}  
  bowerick.test.jms-transport
  (:require
    [bowerick.jms :as jms]
    [bowerick.test.test-helper :as th]
    [clj-assorted-utils.util :as utils]
    [clojure.test :as test]))



(def ^:dynamic *local-jms-server* nil)
(def test-topic "/topic/testtopic.foo")

(defn run-test-with-server [t local-jms-server]
  (println "TESTING:" local-jms-server)
  (let [broker (binding [jms/*trust-store-file* "test/ssl/broker.ts"
                         jms/*trust-store-password* "password"
                         jms/*key-store-file* "test/ssl/broker.ks"
                         jms/*key-store-password* "password"]
                 (th/start-test-broker local-jms-server))]
    (binding [*local-jms-server* local-jms-server]
      (t))
    (jms/stop broker)))

(defn test-set-fixture [t]
  (println "TEST RUN START: " (str t))
  (run-test-with-server t "tcp://127.0.0.1:32324")
  (run-test-with-server t "udp://127.0.0.1:32326")
  (run-test-with-server t "ws://127.0.0.1:32327")
  (run-test-with-server t "ws://127.0.0.1:32329")
  (run-test-with-server t "stomp://127.0.0.1:32322")
  (binding [jms/*trust-store-file* "test/ssl/client.ks"
            jms/*trust-store-password* "password"
            jms/*key-store-file* "test/ssl/client.ks"
            jms/*key-store-password* "password"]
    (run-test-with-server t "stomp+ssl://127.0.0.1:32323")
    (run-test-with-server t "stomp+ssl://127.0.0.1:32323?needClientAuth=true")
    (run-test-with-server t "mqtt+ssl://127.0.0.1:32330")
    (run-test-with-server t "mqtt+ssl://127.0.0.1:32330?needClientAuth=true")
    (run-test-with-server t "wss://127.0.0.1:32328")
    (run-test-with-server t "wss://127.0.0.1:32328/?needClientAuth=true")
    (run-test-with-server t "ssl://localhost:32325")
    (run-test-with-server t "ssl://localhost:32325?needClientAuth=true")))

(test/use-fixtures :each test-set-fixture)



(test/deftest send-string-test
  (let [was-run (utils/prepare-flag)
        consume-fn (fn [_] (utils/set-flag was-run))
        consumer (jms/create-single-consumer *local-jms-server* test-topic consume-fn)
        producer (jms/create-single-producer *local-jms-server* test-topic)]
    (test/is (not (nil? producer)))
    (test/is (not (nil? consumer)))
    (producer "¡Hola!")
    (utils/await-flag was-run)
    (test/is (utils/flag-set? was-run))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest send-byte-array-test
  (let [received (ref nil)
        flag (utils/prepare-flag)
        consume-fn (fn [obj] (dosync (ref-set received obj)) (utils/set-flag flag))
        consumer (jms/create-single-consumer *local-jms-server* test-topic consume-fn)
        data (byte-array (map byte [1 2 3 42]))
        producer (jms/create-single-producer *local-jms-server* test-topic)]
    (test/is (not= data @received))
    (producer data)
    (utils/await-flag flag)
    (doall
      (map
        (fn [a b]
          (test/is
            (= a b)))
        (vec data)
        (vec @received)))
    (jms/close producer)
    (jms/close consumer)))
