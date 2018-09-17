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
    [bowerick.jms :refer :all]
    [bowerick.test.test-helper :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.test :refer :all]))



(def ^:dynamic *local-jms-server* "tcp://127.0.0.1:42424")
(def test-topic "/topic/testtopic.foo")

(defn run-test [t]
  (let [broker (binding [*trust-store-file* "test/ssl/broker.ts"
                         *trust-store-password* "password"
                         *key-store-file* "test/ssl/broker.ks"
                         *key-store-password* "password"]
                 (start-test-broker *local-jms-server*))]
    (t)
    (stop broker)))

(defn single-test-fixture [t]
  (println "TEST RUN START: " (str t))
  (println "TESTING: tcp://127.0.0.1:42424")
  (run-test t)
  (println "TESTING: udp://127.0.0.1:42426")
  (binding [*local-jms-server* "udp://127.0.0.1:42426"]
    (run-test t))
  (println "TESTING: stomp://127.0.0.1:42422")
  (binding [*local-jms-server* "stomp://127.0.0.1:42422"]
    (run-test t))
  (println "TESTING: stomp+ssl://127.0.0.1:42423")
  (binding [*local-jms-server* "stomp+ssl://127.0.0.1:42423"
            *trust-store-file* "test/ssl/client.ts"
            *trust-store-password* "password"
            *key-store-file* "test/ssl/client.ks"
            *key-store-password* "password"]
    (run-test t))
  (println "TESTING: stomp+ssl://127.0.0.1:42423?needClientAuth=true")
  (binding [*local-jms-server* "stomp+ssl://127.0.0.1:42423?needClientAuth=true"
            *trust-store-file* "test/ssl/client.ts"
            *trust-store-password* "password"
            *key-store-file* "test/ssl/client.ks"
            *key-store-password* "password"]
    (run-test t))
  (println "TESTING: ssl://localhost:42425")
  (binding [*local-jms-server* "ssl://localhost:42425"
            *trust-store-file* "test/ssl/client.ts"
            *trust-store-password* "password"
            *key-store-file* "test/ssl/client.ks"
            *key-store-password* "password"]
    (run-test t))
  (println "TESTING: ssl://localhost:42425?needClientAuth=true")
  (binding [*local-jms-server* "ssl://localhost:42425?needClientAuth=true"
            *trust-store-file* "test/ssl/client.ts"
            *trust-store-password* "password"
            *key-store-file* "test/ssl/client.ks"
            *key-store-password* "password"]
    (run-test t)))

(use-fixtures :each single-test-fixture)



(deftest send-list-test
  (let [received (ref nil)
        flag (prepare-flag)
        consume-fn (fn [obj] (dosync (ref-set received obj)) (set-flag flag))
        consumer (create-single-consumer *local-jms-server* test-topic consume-fn)
        data '(:a :b :c)
        producer (create-single-producer *local-jms-server* test-topic)]
    (is (not= data @received))
    (producer data)
    (await-flag flag)
    (is (= data @received))
    (close producer)
    (close consumer)))

(deftest send-vector-test
  (let [received (ref nil)
        flag (prepare-flag)
        consume-fn (fn [obj] (dosync (ref-set received obj)) (set-flag flag))
        consumer (create-single-consumer *local-jms-server* test-topic consume-fn)
        data [1 2 3 4 5]
        producer (create-single-producer *local-jms-server* test-topic)]
    (is (not= data @received))
    (producer data)
    (await-flag flag)
    (is (= data @received))
    (close producer)
    (close consumer)))

