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
  bowerick.test.jms-transport-2
  (:require
    [bowerick.jms :as jms]
    [bowerick.test.test-helper :as th]
    [clj-assorted-utils.util :as utils]
    [clojure.test :as test]))



(def ^:dynamic *local-jms-server* "tcp://127.0.0.1:31324")
(def test-topic "/topic/testtopic.foo")

(defn run-test-with-server [t]
  (let [broker (binding [jms/*trust-store-file* "test/ssl/broker.ts"
                         jms/*trust-store-password* "password"
                         jms/*key-store-file* "test/ssl/broker.ks"
                         jms/*key-store-password* "password"]
                 (th/start-test-broker *local-jms-server*))]
    (t)
    (jms/stop broker)))

(defn single-test-fixture [t]
  (println "TEST RUN START: " (str t))
  (println "TESTING: tcp://127.0.0.1:31324")
  (run-test-with-server t)
  (println "TESTING: udp://127.0.0.1:31326")
  (binding [*local-jms-server* "udp://127.0.0.1:31326"]
    (run-test-with-server t))
  (println "TESTING: stomp://127.0.0.1:31322")
  (binding [*local-jms-server* "stomp://127.0.0.1:31322"]
    (run-test-with-server t))
  (println "TESTING: stomp+ssl://127.0.0.1:31323")
  (binding [*local-jms-server* "stomp+ssl://127.0.0.1:31323"
            jms/*trust-store-file* "test/ssl/client.ts"
            jms/*trust-store-password* "password"
            jms/*key-store-file* "test/ssl/client.ks"
            jms/*key-store-password* "password"]
    (run-test-with-server t))
  (println "TESTING: stomp+ssl://127.0.0.1:31323?needClientAuth=true")
  (binding [*local-jms-server* "stomp+ssl://127.0.0.1:31323?needClientAuth=true"
            jms/*trust-store-file* "test/ssl/client.ts"
            jms/*trust-store-password* "password"
            jms/*key-store-file* "test/ssl/client.ks"
            jms/*key-store-password* "password"]
    (run-test-with-server t))
  (println "TESTING: ssl://localhost:31325")
  (binding [*local-jms-server* "ssl://localhost:31325"
            jms/*trust-store-file* "test/ssl/client.ts"
            jms/*trust-store-password* "password"
            jms/*key-store-file* "test/ssl/client.ks"
            jms/*key-store-password* "password"]
    (run-test-with-server t))
  (println "TESTING: ssl://localhost:31325?needClientAuth=true")
  (binding [*local-jms-server* "ssl://localhost:31325?needClientAuth=true"
            jms/*trust-store-file* "test/ssl/client.ts"
            jms/*trust-store-password* "password"
            jms/*key-store-file* "test/ssl/client.ks"
            jms/*key-store-password* "password"]
    (run-test-with-server t)))

(test/use-fixtures :each single-test-fixture)



(test/deftest send-list-test
  (let [received (ref nil)
        flag (utils/prepare-flag)
        consume-fn (fn [obj] (dosync (ref-set received obj)) (utils/set-flag flag))
        consumer (jms/create-single-consumer *local-jms-server* test-topic consume-fn)
        data '(:a :b :c)
        producer (jms/create-single-producer *local-jms-server* test-topic)]
    (test/is (not= data @received))
    (producer data)
    (utils/await-flag flag)
    (test/is (= data @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest send-vector-test
  (let [received (ref nil)
        flag (utils/prepare-flag)
        consume-fn (fn [obj] (dosync (ref-set received obj)) (utils/set-flag flag))
        consumer (jms/create-single-consumer *local-jms-server* test-topic consume-fn)
        data [1 2 3 4 5]
        producer (jms/create-single-producer *local-jms-server* test-topic)]
    (test/is (not= data @received))
    (producer data)
    (utils/await-flag flag)
    (test/is (= data @received))
    (jms/close producer)
    (jms/close consumer)))

