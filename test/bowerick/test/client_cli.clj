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
    [bowerick.main :as main]
    [bowerick.test.test-helper :as th]
    [cheshire.core :as cheshire]
    [cli4clj.cli-tests :as cli-tests]
    [clj-assorted-utils.util :as utils]
    [clojure.test :as test]
    [clojure.java.io :as io]
    [clojure.string :as s])
  (:import
    (java.io PipedReader PipedWriter)))



(def local-jms-server "tcp://127.0.0.1:42424")
(def local-cli-jms-server "tcp://127.0.0.1:53847")
(def local-cli-ssl-jms-server "ssl://127.0.0.1:53848")
(def test-topic "/topic/testtopic.foo")
(def json-test-file-name "test/data/json-test-data.txt")
;(def csv-test-file-name "test/data/csv_input_test_file.txt")
(def record-test-output-file "record-test-output.txt")
(def replay-test-file-name "test/data/record_out.txt")

(defn test-with-broker [t]
  (utils/rm record-test-output-file)
  (let [broker (th/start-test-broker local-jms-server)]
    (t)
    (jms/stop broker)))

(test/use-fixtures :each test-with-broker)



(test/deftest show-help-test
  (let [out-string (cli-tests/test-cli-stdout #(main/run-cli-app "-h") "")]
    (test/is
      (.startsWith out-string "Bowerick help:"))))

(test/deftest dummy-send-test
  (let [test-cmd-input [(str "send " local-jms-server ":" test-topic " \"test-data\"")]
        out-string (cli-tests/test-cli-stdout #(main/run-cli-app "-c" "-o") test-cmd-input)]
    (test/is
      (=
        (cli-tests/expected-string
          [(str "Sending: " local-jms-server ":" test-topic " <-")
           "\"test-data\""
           "Closing bowerick.jms.ProducerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."])
        out-string))))

(test/deftest dummy-receive-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)]
        out-string (cli-tests/test-cli-stdout #(main/run-cli-app "-c" "-o") test-cmd-input)]
    (test/is
      (=
        (cli-tests/expected-string
          [(str "Set up consumer for: " local-jms-server ":" test-topic)
           "Closing bowerick.jms.ConsumerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."])
        out-string))))

(test/deftest simple-send-receive-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)
                        (str "send " local-jms-server ":" test-topic " \"test-data\"")
                        "_sleep 300"]
        out-string (cli-tests/test-cli-stdout #(main/run-cli-app "-c" "-o") test-cmd-input)]
    (test/is
      (=
        (cli-tests/expected-string
          [(str "Set up consumer for: " local-jms-server ":" test-topic)
           (str "Sending: " local-jms-server ":" test-topic " <-")
           "\"test-data\""
           (str "Received: " local-jms-server ":" test-topic " ->")
           "\"test-data\""
           "Closing bowerick.jms.ProducerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."
           "Closing bowerick.jms.ConsumerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."])
        out-string))))

(test/deftest simple-send-file-receive-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)
                        (str "send-file " local-jms-server ":" test-topic " \"" json-test-file-name "\"")
                        "_sleep 500"]
        out-string (cli-tests/test-cli-stdout #(main/run-cli-app "-c" "-o") test-cmd-input)]
    (test/is
      (=
        (cli-tests/expected-string
          [(str "Set up consumer for: " local-jms-server ":" test-topic)
           (str "Sending file: " local-jms-server ":" test-topic " <- " json-test-file-name)
           (str "Received: " local-jms-server ":" test-topic " ->")
           "{\"a\" \"A\", \"b\" \"B\", \"nested\" {\"x\" 123, \"y\" 1.23}}"
           "Closing bowerick.jms.ProducerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."
           "Closing bowerick.jms.ConsumerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."])
        out-string))))

(test/deftest simple-send-text-file-receive-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)
                        (str "send-text-file " local-jms-server ":" test-topic " \"" json-test-file-name "\"")
                        "_sleep 300"]
        out-string (cli-tests/test-cli-stdout #(main/run-cli-app "-c" "-o") test-cmd-input)]
    (test/is
      (=
        (cli-tests/expected-string
          [(str "Set up consumer for: " local-jms-server ":" test-topic)
           (str "Sending text file: " local-jms-server ":" test-topic " <- " json-test-file-name)
           (str "Received: " local-jms-server ":" test-topic " ->")
           "{\"a\" \"A\", \"b\" \"B\", \"nested\" {\"x\" 123, \"y\" 1.23}}"
           "Closing bowerick.jms.ProducerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."
           "Closing bowerick.jms.ConsumerWrapper for tcp://127.0.0.1:42424:/topic/testtopic.foo ..."])
        out-string))))

(test/deftest simple-send-receive-with-cli-broker-test
  (let [started-string "Broker started."
        started-flag (utils/prepare-flag)
        stopped-string "Broker stopped."
        stopped-flag (utils/prepare-flag)
        stop-wrtr (PipedWriter.)
        stop-rdr (PipedReader. stop-wrtr)
        main-thread (Thread. #(utils/with-out-str-cb
                                (fn [s]
                                  (when (.contains s started-string)
                                    (utils/println-err "Test broker started.")
                                    (utils/set-flag started-flag))
                                  (when (.contains s stopped-string)
                                    (utils/println-err "Test broker stopped.")
                                    (utils/set-flag stopped-flag)))
                                (binding [*in* (io/reader stop-rdr)]
                                  (main/run-cli-app "-u" (str "\"" local-cli-jms-server "\"")))))
        _ (.setDaemon main-thread true)
        _ (.start main-thread)
        _ (println "Waiting for test broker to start up...")
        _ (utils/await-flag started-flag)
        test-cmd-input [(str "receive " local-cli-jms-server ":" test-topic)
                        (str "send " local-cli-jms-server ":" test-topic " \"test-data\"")
                        "_sleep 500"]
        out-string (cli-tests/test-cli-stdout #(main/run-cli-app "-c" "-o") test-cmd-input)]
    (test/is
      (=
        (cli-tests/expected-string
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
    (utils/await-flag stopped-flag)))

(def ssl_files ["broker-cert.pem" "broker.ks" "broker.ts" "broker-certs.ts"
                "client-cert.pem" "client-certreq.pem" "client-key.pem" "client.ks" "client.ts"
                "selfsigned-broker.ks" "selfsigned-client.ks"])
(test/deftest simple-send-receive-with-cli-ssl-self-generated-certificates-broker-test
  (doseq [f ssl_files]
    (println "Deleting:" f)
    (io/delete-file f true))
  (let [started-string "Broker started."
        started-flag (utils/prepare-flag)
        stopped-string "Broker stopped."
        stopped-flag (utils/prepare-flag)
        stop-wrtr (PipedWriter.)
        stop-rdr (PipedReader. stop-wrtr)
        main-thread (Thread. #(utils/with-out-str-cb
                                (fn [s]
                                  (when (.contains s started-string)
                                    (utils/println-err "Test broker started.")
                                    (utils/set-flag started-flag))
                                  (when (.contains s stopped-string)
                                    (utils/println-err "Test broker stopped.")
                                    (utils/set-flag stopped-flag)))
                                (binding [*in* (io/reader stop-rdr)]
                                  (main/run-cli-app "-b" "-u" (str "\"" local-cli-ssl-jms-server "\"")))))
        _ (.setDaemon main-thread true)
        _ (.start main-thread)
        _ (println "Waiting for test broker to start up...")
        _ (utils/await-flag started-flag)
        _ (alter-var-root #'bowerick.jms/*key-store-file* (fn [_ x] x) "client.ks")
        _ (alter-var-root #'bowerick.jms/*trust-store-file* (fn [_ x] x) "client.ts")
        test-cmd-input [(str "receive " local-cli-ssl-jms-server ":" test-topic)
                        (str "send " local-cli-ssl-jms-server ":" test-topic " \"test-data\"")
                        "_sleep 500"]
        out-string (cli-tests/test-cli-stdout #(main/run-cli-app "-k" "-c" "-o") test-cmd-input)]
    (test/is (s/includes? out-string "Importing key and certificates..."))
    (test/is (s/includes? out-string "Broker certificate:"))
    (test/is (s/includes? out-string "-----BEGIN CERTIFICATE-----"))
    (test/is (s/includes? out-string "-----END CERTIFICATE-----"))
    (test/is (s/includes? out-string "Client certificate:"))
    (test/is
     (s/ends-with?
      out-string
      (cli-tests/expected-string
       [(str "Set up consumer for: " local-cli-ssl-jms-server ":" test-topic)
        (str "Sending: " local-cli-ssl-jms-server ":" test-topic " <-")
        "\"test-data\""
        (str "Received: " local-cli-ssl-jms-server ":" test-topic " ->")
        "\"test-data\""
        "Closing bowerick.jms.ProducerWrapper for ssl://127.0.0.1:53848:/topic/testtopic.foo ..."
        "Closing bowerick.jms.ConsumerWrapper for ssl://127.0.0.1:53848:/topic/testtopic.foo ..."])))
    (println "Stopping test broker...")
    (.write stop-wrtr "q\r")
    (println "Waiting for test broker to stop...")
    (utils/await-flag stopped-flag)
    (doseq [f ssl_files]
      (println "Checking file exists:" f)
      (test/is (.exists (io/file f))))))

(test/deftest simple-cli-broker-aframe-test
  (let [started-string "Broker started."
        started-flag (utils/prepare-flag)
        stopped-string "Broker stopped."
        stopped-flag (utils/prepare-flag)
        stop-wrtr (PipedWriter.)
        stop-rdr (PipedReader. stop-wrtr)
        main-thread (Thread. #(utils/with-out-str-cb
                                (fn [s]
                                  (when (string? s)
                                    (when (.contains s started-string)
                                      (utils/println-err "Test broker started.")
                                      (utils/set-flag started-flag))
                                    (when (.contains s stopped-string)
                                      (utils/println-err "Test broker stopped.")
                                      (utils/set-flag stopped-flag))))
                                (binding [*in* (io/reader stop-rdr)]
                                  (main/run-cli-app "-u" (str "\"" local-cli-jms-server "\"") "-A"))))
        _ (.setDaemon main-thread true)
        _ (.start main-thread)
        _ (println "Waiting for test broker to start up...")
        _ (utils/await-flag started-flag)
        received (atom [])
        received-flag (utils/prepare-flag)
        consumer (jms/create-json-consumer
                   local-cli-jms-server
                   "/topic/bowerick.message.generator"
                   (fn [data]
                     (if (>= (count @received) 2)
                       (utils/set-flag received-flag)
                       (swap! received conj data))))]
    (println "Waiting to receive test data...")
    (utils/await-flag received-flag)
    (test/is (seq? (first @received)))
    (test/is (map? (first (first @received))))
    (test/is (contains? (first (first @received)) "x"))
    (test/is (contains? (first (first @received)) "y"))
    (test/is (contains? (first (first @received)) "z"))
    (test/is (> ((first (first @received)) "x") ((first (second @received)) "x")))
    (test/is (< ((first (first @received)) "y") ((first (second @received)) "y")))
    (test/is (= ((first (first @received)) "z") ((first (second @received)) "z")))
    (jms/close consumer)
    (println "Stopping test broker...")
    (.write stop-wrtr "q\r")
    (println "Waiting for test broker to stop...")
    (utils/await-flag stopped-flag)))

(test/deftest simple-cli-broker-embedded-generator-hello-world-test
  (let [started-string "Broker started."
        started-flag (utils/prepare-flag)
        stopped-string "Broker stopped."
        stopped-flag (utils/prepare-flag)
        stop-wrtr (PipedWriter.)
        stop-rdr (PipedReader. stop-wrtr)
        main-thread (Thread. #(utils/with-out-str-cb
                                (fn [s]
                                  (when (string? s)
                                    (when (.contains s started-string)
                                      (utils/println-err "Test broker started.")
                                      (utils/set-flag started-flag))
                                    (when (.contains s stopped-string)
                                      (utils/println-err "Test broker stopped.")
                                      (utils/set-flag stopped-flag))))
                                (binding [*in* (io/reader stop-rdr)]
                                  (main/run-cli-app "-u" (str "\"" local-cli-jms-server "\"") "-G" "hello-world" "-I" "20"))))
        _ (.setDaemon main-thread true)
        _ (.start main-thread)
        _ (println "Waiting for test broker to start up...")
        _ (utils/await-flag started-flag)
        received (atom [])
        received-flag (utils/prepare-flag)
        consumer (jms/create-single-consumer
                   local-cli-jms-server
                   "/topic/bowerick.message.generator"
                   (fn [data]
                     (if (>= (count @received) 2)
                       (utils/set-flag received-flag)
                       (swap! received conj (String. data)))))]
    (println "Waiting to receive test data...")
    (utils/await-flag received-flag)
    (test/is (= "hello world" (first @received)))
    (test/is (= "hello world" (second @received)))
    (jms/close consumer)
    (println "Stopping test broker...")
    (.write stop-wrtr "q\r")
    (println "Waiting for test broker to stop...")
    (utils/await-flag stopped-flag)))

(test/deftest simple-send-receive-with-cli-daemon-broker-test
  (let [started-string "Broker started in daemon mode."
        flag (utils/prepare-flag)
        main-thread (Thread. #(utils/with-out-str-cb
                                (fn [s]
                                  (when (.contains s started-string)
                                    (utils/println-err "Test broker started.")
                                    (utils/set-flag flag)))
                                (main/run-cli-app "-u" (str "\"" local-cli-jms-server "\"") "-d")))
        _ (.setDaemon main-thread true)
        _ (.start main-thread)
        _ (println "Waiting for test broker to start up...")
        _ (utils/await-flag flag)
        test-cmd-input [(str "receive " local-cli-jms-server ":" test-topic)
                        (str "send " local-cli-jms-server ":" test-topic " \"test-data\"")
                        "_sleep 500"]
        out-string (cli-tests/test-cli-stdout #(main/run-cli-app "-c" "-o") test-cmd-input)]
    (test/is
      (=
        (cli-tests/expected-string
          [(str "Set up consumer for: " local-cli-jms-server ":" test-topic)
           (str "Sending: " local-cli-jms-server ":" test-topic " <-")
           "\"test-data\""
           (str "Received: " local-cli-jms-server ":" test-topic " ->")
           "\"test-data\""
           "Closing bowerick.jms.ProducerWrapper for tcp://127.0.0.1:53847:/topic/testtopic.foo ..."
           "Closing bowerick.jms.ConsumerWrapper for tcp://127.0.0.1:53847:/topic/testtopic.foo ..."])
        out-string))
    (.interrupt main-thread)))

(test/deftest broker-management-get-destinations-test
  (let [test-cmd-input [(str "management \"" local-jms-server "\" get-destinations")
                        "_sleep 300"]
        out-string (cli-tests/test-cli-stdout #(main/run-cli-app "-c" "-o") test-cmd-input)]
    (test/is
      (=
        (cli-tests/expected-string
          ["Management Command: tcp://127.0.0.1:42424:/topic/bowerick.broker.management.command <-"
           "\"get-destinations\""
           "Management Reply: tcp://127.0.0.1:42424 ->"
           "(\"/topic/bowerick.broker.management.command\""
           " \"/topic/bowerick.broker.management.reply\")"
           "Closing bowerick.jms.ProducerWrapper for tcp://127.0.0.1:42424:/topic/bowerick.broker.management.command ..."
           "Closing bowerick.jms.ConsumerWrapper for tcp://127.0.0.1:42424:/topic/bowerick.broker.management.reply ..."])
        out-string))))

(test/deftest broker-management-get-all-destinations-test
  (let [test-cmd-input [(str "management " local-jms-server " get-all-destinations")
                        "_sleep 300"]
        out-string (cli-tests/test-cli-stdout #(main/run-cli-app "-c" "-o") test-cmd-input)]
    (test/is
      (=
        (cli-tests/expected-string
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

(test/deftest simple-record-test
  (test/is (not (utils/file-exists? record-test-output-file)))
  (let [test-cmd-input [(str "record " record-test-output-file " " local-jms-server ":" test-topic)
                        "_sleep 300"
                        (str "send " local-jms-server ":" test-topic " foo")
                        (str "send " local-jms-server ":" test-topic " bar")
                        (str "send " local-jms-server ":" test-topic " 123")
                        "_sleep 300"
                        (str "stop " record-test-output-file)]
        _ (cli-tests/test-cli-stdout #(main/run-cli-app "-c" "-o") test-cmd-input)
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
    (test/is (utils/file-exists? record-test-output-file))
    (doall
      (map
        (fn [exp act]
          (test/is (= (exp "data") (act "data")))
          (test/is (= (get-in exp ["metadata" "source"]) (get-in act ["metadata" "source"])))
          (test/is (not (nil? (get-in act ["metadata" "timestamp"])))))
        expected-data
        (recorded-data "messages")))))

(test/deftest record-stop-through-quit-test
  (test/is (not (utils/file-exists? record-test-output-file)))
  (let [test-cmd-input [(str "record " record-test-output-file " " local-jms-server ":" test-topic)
                        "_sleep 300"
                        (str "send " local-jms-server ":" test-topic " foo")
                        (str "send " local-jms-server ":" test-topic " bar")
                        (str "send " local-jms-server ":" test-topic " 123")
                        "_sleep 300"
                        (str "quit " record-test-output-file)]
        _ (cli-tests/test-cli-stdout #(main/run-cli-app "-c" "-o") test-cmd-input)
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
    (test/is (utils/file-exists? record-test-output-file))
    (doall
      (map
        (fn [exp act]
          (test/is (= (exp "data") (act "data")))
          (test/is (= (get-in exp ["metadata" "source"]) (get-in act ["metadata" "source"])))
          (test/is (not (nil? (get-in act ["metadata" "timestamp"])))))
        expected-data
        (recorded-data "messages")))))

(test/deftest simple-multi-destination-record-test
  (test/is (not (utils/file-exists? record-test-output-file)))
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
        _ (cli-tests/test-cli-stdout #(main/run-cli-app "-c" "-o") test-cmd-input)
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
    (test/is (utils/file-exists? record-test-output-file))
    (doall
      (map
        (fn [exp act]
          (test/is (= (exp "data") (act "data")))
          (test/is (= (get-in exp ["metadata" "source"]) (get-in act ["metadata" "source"])))
          (test/is (not (nil? (get-in act ["metadata" "timestamp"])))))
        expected-data
        (recorded-data "messages")))
    (test/is (not (nil? (get-in recorded-data ["metadata" "timestamp_millis"]))))
    (test/is (not (nil? (get-in recorded-data ["metadata" "timestamp_nanos"]))))))

(test/deftest multi-destination-multi-arg-record-test
  (test/is (not (utils/file-exists? record-test-output-file)))
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
        _ (cli-tests/test-cli-stdout #(main/run-cli-app "-c" "-o") test-cmd-input)
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
    (test/is (utils/file-exists? record-test-output-file))
    (doall
      (map
        (fn [exp act]
          (test/is (= (exp "data") (act "data")))
          (test/is (= (get-in exp ["metadata" "source"]) (get-in act ["metadata" "source"])))
          (test/is (not (nil? (get-in act ["metadata" "timestamp"])))))
        expected-data
        (recorded-data "messages")))
    (test/is (not (nil? (get-in recorded-data ["metadata" "timestamp_millis"]))))
    (test/is (not (nil? (get-in recorded-data ["metadata" "timestamp_nanos"]))))))

(test/deftest simple-replay-file-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)
                        "_sleep 300"
                        (str "replay \"" replay-test-file-name "\" 100 false")
                        "_sleep 600"]
        out-string (cli-tests/test-cli-stdout #(main/run-cli-app "-c" "-o") test-cmd-input)]
    (test/is
      (=
        (cli-tests/expected-string
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

(test/deftest simple-looped-replay-file-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)
                        "_sleep 300"
                        (str "replay \"" replay-test-file-name "\" 400 true")
                        "_sleep 600"]
        out-string (cli-tests/test-cli-stdout #(main/run-cli-app "-c" "-o") test-cmd-input)]
    (test/is
      (=
        (cli-tests/expected-string
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
