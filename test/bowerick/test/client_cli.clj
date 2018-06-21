;;;
;;;   Copyright 2016 Ruediger Gad
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Tests for the client CLI"}  
  bowerick.test.client-cli
  (:require
    [bowerick.jms :refer :all]
    [bowerick.main :refer :all]
    [bowerick.test.test-helper :refer :all]
    [cli4clj.cli-tests :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.test :refer :all]
    [clojure.java.io :as io])
  (:import
    (java.io PipedReader PipedWriter)))



(def local-jms-server "tcp://127.0.0.1:42424")
(def local-cli-jms-server "tcp://127.0.0.1:53847")
(def test-topic "/topic/testtopic.foo")
(def json-test-file-name "test/data/json-test-data.txt")

(defn test-with-broker [t]
  (let [broker (start-test-broker local-jms-server)]
    (t)
    (stop broker)))

(use-fixtures :each test-with-broker)



(deftest show-help-test
  (let [out-string (test-cli-stdout #(-main "-h") "")]
    (is
      (.startsWith out-string "Bowerick help:"))))

(deftest dummy-send-test
  (let [test-cmd-input [(str "send " local-jms-server ":" test-topic " \"test-data\"")]
        out-string (test-cli-stdout #(-main "-c") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Sending: " local-jms-server ":" test-topic " <-")
           "\"test-data\""])
        out-string))))

(deftest dummy-receive-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)]
        out-string (test-cli-stdout #(-main "-c") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Set up consumer for: " local-jms-server ":" test-topic)])
        out-string))))

(deftest simple-send-receive-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)
                        (str "send " local-jms-server ":" test-topic " \"test-data\"")
                        "_sleep 300"]
        out-string (test-cli-stdout #(-main "-c") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Set up consumer for: " local-jms-server ":" test-topic)
           (str "Sending: " local-jms-server ":" test-topic " <-")
           "\"test-data\""
           (str "Received: " local-jms-server ":" test-topic " ->")
           "\"test-data\""])
        out-string))))

(deftest simple-send-file-receive-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)
                        (str "send-file " local-jms-server ":" test-topic " \"" json-test-file-name "\"")
                        "_sleep 300"]
        out-string (test-cli-stdout #(-main "-c") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Set up consumer for: " local-jms-server ":" test-topic)
           (str "Sending file: " local-jms-server ":" test-topic " <- " json-test-file-name)
           (str "Received: " local-jms-server ":" test-topic " ->")
           "{\"a\" \"A\", \"b\" \"B\", \"nested\" {\"x\" 123, \"y\" 1.23}}"])
        out-string))))

(deftest simple-send-receive-with-cli-broker-test
  (let [started-string "Broker started... Type \"q\" followed by <Return> to quit:"
        started-flag (prepare-flag)
        stopped-string "Broker stopped."
        stopped-flag (prepare-flag)
        stop-wrtr (PipedWriter.)
        stop-rdr (PipedReader. stop-wrtr)
        main-thread (Thread. #(with-out-str-cb
                                (fn [s]
                                  (when (.contains s started-string)
                                    (println-err "Test broker started.")
                                    (set-flag started-flag))
                                  (when (.contains s stopped-string)
                                    (println-err "Test broker stopped.")
                                    (set-flag stopped-flag)))
                                (binding [*in* (io/reader stop-rdr)]
                                  (-main "-u" (str "\"" local-cli-jms-server "\"")))))
        _ (.setDaemon main-thread true)
        _ (.start main-thread)
        _ (println "Waiting for test broker to start up...")
        _ (await-flag started-flag)
        test-cmd-input [(str "receive " local-cli-jms-server ":" test-topic)
                        (str "send " local-cli-jms-server ":" test-topic " \"test-data\"")
                        "_sleep 500"]
        out-string (test-cli-stdout #(-main "-c") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Set up consumer for: " local-cli-jms-server ":" test-topic)
           (str "Sending: " local-cli-jms-server ":" test-topic " <-")
           "\"test-data\""
           (str "Received: " local-cli-jms-server ":" test-topic " ->")
           "\"test-data\""])
        out-string))
    (println "Stopping test broker...")
    (.write stop-wrtr "q\r")
    (println "Waiting for test broker to stop...")
    (await-flag stopped-flag)))

(deftest simple-cli-broker-aframe-test
  (let [started-string "Broker started... Type \"q\" followed by <Return> to quit:"
        started-flag (prepare-flag)
        stopped-string "Broker stopped."
        stopped-flag (prepare-flag)
        stop-wrtr (PipedWriter.)
        stop-rdr (PipedReader. stop-wrtr)
        main-thread (Thread. #(with-out-str-cb
                                (fn [s]
                                  (when (string? s)
                                    (when (.contains s started-string)
                                      (println-err "Test broker started.")
                                      (set-flag started-flag))
                                    (when (.contains s stopped-string)
                                      (println-err "Test broker stopped.")
                                      (set-flag stopped-flag))))
                                (binding [*in* (io/reader stop-rdr)]
                                  (-main "-u" (str "\"" local-cli-jms-server "\"") "-A"))))
        _ (.setDaemon main-thread true)
        _ (.start main-thread)
        _ (println "Waiting for test broker to start up...")
        _ (await-flag started-flag)
        received (atom [])
        received-flag (prepare-flag)
        consumer (create-json-consumer
                   local-cli-jms-server
                   "/topic/aframe"
                   (fn [data]
                     (if (>= (count @received) 2)
                       (set-flag received-flag)
                       (swap! received conj data))))]
    (println "Waiting to receive test data...")
    (await-flag received-flag)
    (is (map? (first @received)))
    (is (contains? (first @received) "x"))
    (is (contains? (first @received) "y"))
    (is (contains? (first @received) "z"))
    (is (> ((first @received) "x") ((second @received) "x")))
    (is (< ((first @received) "y") ((second @received) "y")))
    (is (= ((first @received) "z") ((second @received) "z")))
    (println "Stopping test broker...")
    (.write stop-wrtr "q\r")
    (println "Waiting for test broker to stop...")
    (await-flag stopped-flag)))

(deftest simple-cli-broker-embedded-generator-hello-world-test
  (let [started-string "Broker started... Type \"q\" followed by <Return> to quit:"
        started-flag (prepare-flag)
        stopped-string "Broker stopped."
        stopped-flag (prepare-flag)
        stop-wrtr (PipedWriter.)
        stop-rdr (PipedReader. stop-wrtr)
        main-thread (Thread. #(with-out-str-cb
                                (fn [s]
                                  (when (string? s)
                                    (when (.contains s started-string)
                                      (println-err "Test broker started.")
                                      (set-flag started-flag))
                                    (when (.contains s stopped-string)
                                      (println-err "Test broker stopped.")
                                      (set-flag stopped-flag))))
                                (binding [*in* (io/reader stop-rdr)]
                                  (-main "-u" (str "\"" local-cli-jms-server "\"") "-G" "hello-world" "-I" "20"))))
        _ (.setDaemon main-thread true)
        _ (.start main-thread)
        _ (println "Waiting for test broker to start up...")
        _ (await-flag started-flag)
        received (atom [])
        received-flag (prepare-flag)
        consumer (create-single-consumer
                   local-cli-jms-server
                   "/topic/bowerick.message.generator"
                   (fn [data]
                     (if (>= (count @received) 2)
                       (set-flag received-flag)
                       (swap! received conj (String. data)))))]
    (println "Waiting to receive test data...")
    (await-flag received-flag)
    (is (= "hello world" (first @received)))
    (is (= "hello world" (second @received)))
    (println "Stopping test broker...")
    (.write stop-wrtr "q\r")
    (println "Waiting for test broker to stop...")
    (await-flag stopped-flag)))

(deftest simple-send-receive-with-cli-daemon-broker-test
  (let [started-string "Broker started in daemon mode."
        flag (prepare-flag)
        main-thread (Thread. #(with-out-str-cb
                                (fn [s]
                                  (when (.contains s started-string)
                                    (println-err "Test broker started.")
                                    (set-flag flag)))
                                (-main "-u" (str "\"" local-cli-jms-server "\"") "-d")))
        _ (.setDaemon main-thread true)
        _ (.start main-thread)
        _ (println "Waiting for test broker to start up...")
        _ (await-flag flag)
        test-cmd-input [(str "receive " local-cli-jms-server ":" test-topic)
                        (str "send " local-cli-jms-server ":" test-topic " \"test-data\"")
                        "_sleep 500"]
        out-string (test-cli-stdout #(-main "-c") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Set up consumer for: " local-cli-jms-server ":" test-topic)
           (str "Sending: " local-cli-jms-server ":" test-topic " <-")
           "\"test-data\""
           (str "Received: " local-cli-jms-server ":" test-topic " ->")
           "\"test-data\""])
        out-string))
    (.interrupt main-thread)))

(deftest broker-management-get-destinations-test
  (let [test-cmd-input [(str "management \"" local-jms-server "\" get-destinations")
                        "_sleep 300"]
        out-string (test-cli-stdout #(-main "-c") test-cmd-input)]
    (is
      (=
        (expected-string
          ["Management Command: tcp://127.0.0.1:42424:/topic/bowerick.broker.management.command <-"
           "\"get-destinations\""
           "Management Reply: tcp://127.0.0.1:42424 ->"
           "(\"/topic/bowerick.broker.management.command\""
           " \"/topic/bowerick.broker.management.reply\")"])
        out-string))))

(deftest broker-management-get-all-destinations-test
  (let [test-cmd-input [(str "management " local-jms-server " get-all-destinations")
                        "_sleep 300"]
        out-string (test-cli-stdout #(-main "-c") test-cmd-input)]
    (is
      (=
        (expected-string
          ["Management Command: tcp://127.0.0.1:42424:/topic/bowerick.broker.management.command <-"
           "\"get-all-destinations\""
           "Management Reply: tcp://127.0.0.1:42424 ->"
           "(\"/topic/ActiveMQ.Advisory.Connection\""
           " \"/topic/ActiveMQ.Advisory.Consumer.Topic.bowerick.broker.management.command\""
           " \"/topic/ActiveMQ.Advisory.Consumer.Topic.bowerick.broker.management.reply\""
           " \"/topic/ActiveMQ.Advisory.MasterBroker\""
           " \"/topic/ActiveMQ.Advisory.Producer.Topic.bowerick.broker.management.command\""
           " \"/topic/ActiveMQ.Advisory.Producer.Topic.bowerick.broker.management.reply\""
           " \"/topic/ActiveMQ.Advisory.Topic\""
           " \"/topic/bowerick.broker.management.command\""
           " \"/topic/bowerick.broker.management.reply\")"])
        out-string))))

