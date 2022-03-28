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
    [bowerick.jms :as jms]
    [bowerick.main :refer :all]
    [bowerick.test.test-helper :refer :all]
    [cheshire.core :as cheshire]
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
(def csv-test-file-name "test/data/csv_input_test_file.txt")
(def record-test-output-file "record-test-output.txt")
(def replay-test-file-name "test/data/record_out.txt")

(defn test-with-broker [t]
  (rm record-test-output-file)
  (let [broker (start-test-broker local-jms-server)]
    (t)
    (jms/stop broker)))

(use-fixtures :each test-with-broker)



(deftest show-help-test
  (let [out-string (test-cli-stdout #(run-cli-app "-h") "")]
    (is
      (.startsWith out-string "Bowerick help:"))))

(deftest dummy-send-test
  (let [test-cmd-input [(str "send " local-jms-server ":" test-topic " \"test-data\"")]
        out-string (test-cli-stdout #(run-cli-app "-c" "-o") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Sending: " local-jms-server ":" test-topic " <-")
           "\"test-data\""
           "Closing bowerick.jms.ProducerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."])
        out-string))))

(deftest dummy-receive-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)]
        out-string (test-cli-stdout #(run-cli-app "-c" "-o") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Set up consumer for: " local-jms-server ":" test-topic)
           "Closing bowerick.jms.ConsumerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."])
        out-string))))

(deftest simple-send-receive-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)
                        (str "send " local-jms-server ":" test-topic " \"test-data\"")
                        "_sleep 300"]
        out-string (test-cli-stdout #(run-cli-app "-c" "-o") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Set up consumer for: " local-jms-server ":" test-topic)
           (str "Sending: " local-jms-server ":" test-topic " <-")
           "\"test-data\""
           (str "Received: " local-jms-server ":" test-topic " ->")
           "\"test-data\""
           "Closing bowerick.jms.ProducerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."
           "Closing bowerick.jms.ConsumerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."])
        out-string))))

(deftest simple-send-file-receive-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)
                        (str "send-file " local-jms-server ":" test-topic " \"" json-test-file-name "\"")
                        "_sleep 500"]
        out-string (test-cli-stdout #(run-cli-app "-c" "-o") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Set up consumer for: " local-jms-server ":" test-topic)
           (str "Sending file: " local-jms-server ":" test-topic " <- " json-test-file-name)
           (str "Received: " local-jms-server ":" test-topic " ->")
           "{\"a\" \"A\", \"b\" \"B\", \"nested\" {\"x\" 123, \"y\" 1.23}}"
           "Closing bowerick.jms.ProducerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."
           "Closing bowerick.jms.ConsumerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."])
        out-string))))

(deftest simple-send-text-file-receive-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)
                        (str "send-text-file " local-jms-server ":" test-topic " \"" json-test-file-name "\"")
                        "_sleep 300"]
        out-string (test-cli-stdout #(run-cli-app "-c" "-o") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Set up consumer for: " local-jms-server ":" test-topic)
           (str "Sending text file: " local-jms-server ":" test-topic " <- " json-test-file-name)
           (str "Received: " local-jms-server ":" test-topic " ->")
           "{\"a\" \"A\", \"b\" \"B\", \"nested\" {\"x\" 123, \"y\" 1.23}}"
           "Closing bowerick.jms.ProducerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."
           "Closing bowerick.jms.ConsumerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."])
        out-string))))

(deftest simple-send-receive-with-cli-broker-test
  (let [started-string "Broker started."
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
                                  (run-cli-app "-u" (str "\"" local-cli-jms-server "\"")))))
        _ (.setDaemon main-thread true)
        _ (.start main-thread)
        _ (println "Waiting for test broker to start up...")
        _ (await-flag started-flag)
        test-cmd-input [(str "receive " local-cli-jms-server ":" test-topic)
                        (str "send " local-cli-jms-server ":" test-topic " \"test-data\"")
                        "_sleep 500"]
        out-string (test-cli-stdout #(run-cli-app "-c" "-o") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Set up consumer for: " local-cli-jms-server ":" test-topic)
           (str "Sending: " local-cli-jms-server ":" test-topic " <-")
           "\"test-data\""
           (str "Received: " local-cli-jms-server ":" test-topic " ->")
           "\"test-data\""
           "Closing bowerick.jms.ProducerWrapper for tcp://127.0.0.1:53847:/topic/testtopic.foo ..."
           "Closing bowerick.jms.ConsumerWrapper for tcp://127.0.0.1:53847:/topic/testtopic.foo ..."])
        out-string))
    (println "Stopping test broker...")
    (.write stop-wrtr "q\r")
    (println "Waiting for test broker to stop...")
    (await-flag stopped-flag)))

(deftest simple-cli-broker-aframe-test
  (let [started-string "Broker started."
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
                                  (run-cli-app "-u" (str "\"" local-cli-jms-server "\"") "-A"))))
        _ (.setDaemon main-thread true)
        _ (.start main-thread)
        _ (println "Waiting for test broker to start up...")
        _ (await-flag started-flag)
        received (atom [])
        received-flag (prepare-flag)
        consumer (jms/create-json-consumer
                   local-cli-jms-server
                   "/topic/bowerick.message.generator"
                   (fn [data]
                     (if (>= (count @received) 2)
                       (set-flag received-flag)
                       (swap! received conj data))))]
    (println "Waiting to receive test data...")
    (await-flag received-flag)
    (is (seq? (first @received)))
    (is (map? (first (first @received))))
    (is (contains? (first (first @received)) "x"))
    (is (contains? (first (first @received)) "y"))
    (is (contains? (first (first @received)) "z"))
    (is (> ((first (first @received)) "x") ((first (second @received)) "x")))
    (is (< ((first (first @received)) "y") ((first (second @received)) "y")))
    (is (= ((first (first @received)) "z") ((first (second @received)) "z")))
    (println "Stopping test broker...")
    (.write stop-wrtr "q\r")
    (println "Waiting for test broker to stop...")
    (await-flag stopped-flag)))

(deftest simple-cli-broker-embedded-generator-hello-world-test
  (let [started-string "Broker started."
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
                                  (run-cli-app "-u" (str "\"" local-cli-jms-server "\"") "-G" "hello-world" "-I" "20"))))
        _ (.setDaemon main-thread true)
        _ (.start main-thread)
        _ (println "Waiting for test broker to start up...")
        _ (await-flag started-flag)
        received (atom [])
        received-flag (prepare-flag)
        consumer (jms/create-single-consumer
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
                                (run-cli-app "-u" (str "\"" local-cli-jms-server "\"") "-d")))
        _ (.setDaemon main-thread true)
        _ (.start main-thread)
        _ (println "Waiting for test broker to start up...")
        _ (await-flag flag)
        test-cmd-input [(str "receive " local-cli-jms-server ":" test-topic)
                        (str "send " local-cli-jms-server ":" test-topic " \"test-data\"")
                        "_sleep 500"]
        out-string (test-cli-stdout #(run-cli-app "-c" "-o") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Set up consumer for: " local-cli-jms-server ":" test-topic)
           (str "Sending: " local-cli-jms-server ":" test-topic " <-")
           "\"test-data\""
           (str "Received: " local-cli-jms-server ":" test-topic " ->")
           "\"test-data\""
           "Closing bowerick.jms.ProducerWrapper for tcp://127.0.0.1:53847:/topic/testtopic.foo ..."
           "Closing bowerick.jms.ConsumerWrapper for tcp://127.0.0.1:53847:/topic/testtopic.foo ..."])
        out-string))
    (.interrupt main-thread)))

(deftest broker-management-get-destinations-test
  (let [test-cmd-input [(str "management \"" local-jms-server "\" get-destinations")
                        "_sleep 300"]
        out-string (test-cli-stdout #(run-cli-app "-c" "-o") test-cmd-input)]
    (is
      (=
        (expected-string
          ["Management Command: tcp://127.0.0.1:42424:/topic/bowerick.broker.management.command <-"
           "\"get-destinations\""
           "Management Reply: tcp://127.0.0.1:42424 ->"
           "(\"/topic/bowerick.broker.management.command\""
           " \"/topic/bowerick.broker.management.reply\")"
           "Closing bowerick.jms.ProducerWrapper for tcp://127.0.0.1:42424:/topic/bowerick.broker.management.command ..."
           "Closing bowerick.jms.ConsumerWrapper for tcp://127.0.0.1:42424:/topic/bowerick.broker.management.reply ..."])
        out-string))))

(deftest broker-management-get-all-destinations-test
  (let [test-cmd-input [(str "management " local-jms-server " get-all-destinations")
                        "_sleep 300"]
        out-string (test-cli-stdout #(run-cli-app "-c" "-o") test-cmd-input)]
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
           " \"/topic/bowerick.broker.management.reply\")"
           "Closing bowerick.jms.ProducerWrapper for tcp://127.0.0.1:42424:/topic/bowerick.broker.management.command ..."
           "Closing bowerick.jms.ConsumerWrapper for tcp://127.0.0.1:42424:/topic/bowerick.broker.management.reply ..."])
        out-string))))

(deftest simple-record-test
  (is (not (file-exists? record-test-output-file)))
  (let [test-cmd-input [(str "record " record-test-output-file " " local-jms-server ":" test-topic)
                        "_sleep 300"
                        (str "send " local-jms-server ":" test-topic " foo")
                        (str "send " local-jms-server ":" test-topic " bar")
                        (str "send " local-jms-server ":" test-topic " 123")
                        "_sleep 300"
                        (str "stop " record-test-output-file)]
        out-string (test-cli-stdout #(run-cli-app "-c" "-o") test-cmd-input)
        expected-data [{"data" "\"foo\""
                        "metadata" {"source" (str local-jms-server ":" test-topic)
                                    "msg-class" "class org.apache.activemq.command.ActiveMQBytesMessage"}}
                       {"data" "\"bar\""
                        "metadata" {"source" (str local-jms-server ":" test-topic)
                                    "msg-class" "class org.apache.activemq.command.ActiveMQBytesMessage"}}
                       {"data" "123"
                        "metadata" {"source" (str local-jms-server ":" test-topic)
                                    "msg-class" "class org.apache.activemq.command.ActiveMQBytesMessage"}}]
        recorded-data (cheshire/parse-string (slurp record-test-output-file))]
    (is (file-exists? record-test-output-file))
    (doall
      (map
        (fn [exp act]
          (is (= (exp "data") (act "data")))
          (is (= (get-in exp ["metadata" "source"]) (get-in act ["metadata" "source"])))
          (is (not (nil? (get-in act ["metadata" "timestamp"])))))
        expected-data
        (recorded-data "messages")))))

(deftest record-stop-through-quit-test
  (is (not (file-exists? record-test-output-file)))
  (let [test-cmd-input [(str "record " record-test-output-file " " local-jms-server ":" test-topic)
                        "_sleep 300"
                        (str "send " local-jms-server ":" test-topic " foo")
                        (str "send " local-jms-server ":" test-topic " bar")
                        (str "send " local-jms-server ":" test-topic " 123")
                        "_sleep 300"
                        (str "quit " record-test-output-file)]
        out-string (test-cli-stdout #(run-cli-app "-c" "-o") test-cmd-input)
        expected-data [{"data" "\"foo\""
                        "metadata" {"source" (str local-jms-server ":" test-topic)
                                    "msg-class" "class org.apache.activemq.command.ActiveMQBytesMessage"}}
                       {"data" "\"bar\""
                        "metadata" {"source" (str local-jms-server ":" test-topic)
                                    "msg-class" "class org.apache.activemq.command.ActiveMQBytesMessage"}}
                       {"data" "123"
                        "metadata" {"source" (str local-jms-server ":" test-topic)
                                    "msg-class" "class org.apache.activemq.command.ActiveMQBytesMessage"}}]
        recorded-data (cheshire/parse-string (slurp record-test-output-file))]
    (is (file-exists? record-test-output-file))
    (doall
      (map
        (fn [exp act]
          (is (= (exp "data") (act "data")))
          (is (= (get-in exp ["metadata" "source"]) (get-in act ["metadata" "source"])))
          (is (not (nil? (get-in act ["metadata" "timestamp"])))))
        expected-data
        (recorded-data "messages")))))

(deftest simple-multi-destination-record-test
  (is (not (file-exists? record-test-output-file)))
  (let [test-cmd-input [(str "record " record-test-output-file " " local-jms-server ":" test-topic ".a")
                        (str "record " record-test-output-file " " local-jms-server ":" test-topic ".b")
                        (str "record " record-test-output-file " " local-jms-server ":" test-topic ".c")
                        "_sleep 300"
                        (str "send " local-jms-server ":" test-topic ".a foo")
                        "_sleep 100"
                        (str "send " local-jms-server ":" test-topic ".b bar")
                        "_sleep 100"
                        (str "send " local-jms-server ":" test-topic ".c 123")
                        "_sleep 300"
                        (str "stop " record-test-output-file)]
        out-string (test-cli-stdout #(run-cli-app "-c" "-o") test-cmd-input)
        expected-data [{"data" "\"foo\""
                        "metadata" {"source" (str local-jms-server ":" test-topic ".a")
                                    "msg-class" "class org.apache.activemq.command.ActiveMQBytesMessage"}}
                       {"data" "\"bar\""
                        "metadata" {"source" (str local-jms-server ":" test-topic ".b")
                                    "msg-class" "class org.apache.activemq.command.ActiveMQBytesMessage"}}
                       {"data" "123"
                        "metadata" {"source" (str local-jms-server ":" test-topic ".c")
                                    "msg-class" "class org.apache.activemq.command.ActiveMQBytesMessage"}}]
        recorded-data (cheshire/parse-string (slurp record-test-output-file))]
    (is (file-exists? record-test-output-file))
    (doall
      (map
        (fn [exp act]
          (is (= (exp "data") (act "data")))
          (is (= (get-in exp ["metadata" "source"]) (get-in act ["metadata" "source"])))
          (is (not (nil? (get-in act ["metadata" "timestamp"])))))
        expected-data
        (recorded-data "messages")))
    (is (not (nil? (get-in recorded-data ["metadata" "timestamp_millis"]))))
    (is (not (nil? (get-in recorded-data ["metadata" "timestamp_nanos"]))))))

(deftest multi-destination-multi-arg-record-test
  (is (not (file-exists? record-test-output-file)))
  (let [test-cmd-input [(str
                          "record "
                          record-test-output-file " "
                          local-jms-server ":" test-topic ".a "
                          local-jms-server ":" test-topic ".b "
                          local-jms-server ":" test-topic ".c")
                        "_sleep 300"
                        (str "send " local-jms-server ":" test-topic ".a foo")
                        (str "send " local-jms-server ":" test-topic ".b bar")
                        (str "send " local-jms-server ":" test-topic ".c 123")
                        "_sleep 300"
                        (str "stop " record-test-output-file)]
        out-string (test-cli-stdout #(run-cli-app "-c" "-o") test-cmd-input)
        expected-data [{"data" "\"foo\""
                        "metadata" {"source" (str local-jms-server ":" test-topic ".a")
                                    "msg-class" "class org.apache.activemq.command.ActiveMQBytesMessage"}}
                       {"data" "\"bar\""
                        "metadata" {"source" (str local-jms-server ":" test-topic ".b")
                                    "msg-class" "class org.apache.activemq.command.ActiveMQBytesMessage"}}
                       {"data" "123"
                        "metadata" {"source" (str local-jms-server ":" test-topic ".c")
                                    "msg-class" "class org.apache.activemq.command.ActiveMQBytesMessage"}}]
        recorded-data (cheshire/parse-string (slurp record-test-output-file))]
    (is (file-exists? record-test-output-file))
    (doall
      (map
        (fn [exp act]
          (is (= (exp "data") (act "data")))
          (is (= (get-in exp ["metadata" "source"]) (get-in act ["metadata" "source"])))
          (is (not (nil? (get-in act ["metadata" "timestamp"])))))
        expected-data
        (recorded-data "messages")))
    (is (not (nil? (get-in recorded-data ["metadata" "timestamp_millis"]))))
    (is (not (nil? (get-in recorded-data ["metadata" "timestamp_nanos"]))))))

(deftest simple-replay-file-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)
                        "_sleep 300"
                        (str "replay \"" replay-test-file-name "\" 100 false")
                        "_sleep 600"]
        out-string (test-cli-stdout #(run-cli-app "-c" "-o") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Set up consumer for: " local-jms-server ":" test-topic)
           (str "Replaying from file: " replay-test-file-name)
           "Replaying 3 messages using reference time: 63103447065280"
           (str "Received: " local-jms-server ":" test-topic " ->")
           "\"abc\""
           (str "Received: " local-jms-server ":" test-topic " ->")
           "{\"a\" \"A\", \"b\" 123, \"c\" 1.23}"
           (str "Received: " local-jms-server ":" test-topic " ->")
           "(\"a\" \"b\" 3 4.2)"
           "Closing bowerick.jms.ProducerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."
           "Closing bowerick.jms.ConsumerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."])
        out-string))))

(deftest simple-looped-replay-file-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)
                        "_sleep 300"
                        (str "replay \"" replay-test-file-name "\" 400 true")
                        "_sleep 600"]
        out-string (test-cli-stdout #(run-cli-app "-c" "-o") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Set up consumer for: " local-jms-server ":" test-topic)
           (str "Replaying from file: " replay-test-file-name)
           "Replaying 3 messages using reference time: 63103447065280"
           (str "Received: " local-jms-server ":" test-topic " ->")
           "\"abc\""
           (str "Received: " local-jms-server ":" test-topic " ->")
           "{\"a\" \"A\", \"b\" 123, \"c\" 1.23}"
           (str "Received: " local-jms-server ":" test-topic " ->")
           "(\"a\" \"b\" 3 4.2)"
           (str "Received: " local-jms-server ":" test-topic " ->")
           "\"abc\""
           (str "Received: " local-jms-server ":" test-topic " ->")
           "{\"a\" \"A\", \"b\" 123, \"c\" 1.23}"
           (str "Received: " local-jms-server ":" test-topic " ->")
           "(\"a\" \"b\" 3 4.2)"
           "Closing bowerick.jms.ProducerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."
           "Closing bowerick.jms.ConsumerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."])
        out-string))))

