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
    [cli4clj.cli-tests :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.test :refer :all] clojure.test))



(def local-jms-server "tcp://127.0.0.1:42424")
(def local-cli-jms-server "tcp://127.0.0.1:53847")
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (start-broker local-jms-server)]
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

(deftest simple-send-receive-with-cli-broker-test
  (let [_ (run-once (executor) #(-main "-u" (str "\"" local-cli-jms-server "\"")) 0)
        test-cmd-input [(str "receive " local-cli-jms-server ":" test-topic)
                        (str "send " local-cli-jms-server ":" test-topic " \"test-data\"")
                        "_sleep 300"]
        out-string (test-cli-stdout #(-main "-c") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Set up consumer for: " local-cli-jms-server ":" test-topic)
           (str "Sending: " local-cli-jms-server ":" test-topic " <-")
           "\"test-data\""
           (str "Received: " local-cli-jms-server ":" test-topic " ->")
           "\"test-data\""])
        out-string))))

(deftest simple-send-receive-with-cli-daemon-broker-test
  (let [_ (run-once (executor) #(-main "-u" (str "\"" local-cli-jms-server "\"") "-d") 0)
        test-cmd-input [(str "receive " local-cli-jms-server ":" test-topic)
                        (str "send " local-cli-jms-server ":" test-topic " \"test-data\"")
                        "_sleep 300"]
        out-string (test-cli-stdout #(-main "-c") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Set up consumer for: " local-cli-jms-server ":" test-topic)
           (str "Sending: " local-cli-jms-server ":" test-topic " <-")
           "\"test-data\""
           (str "Received: " local-cli-jms-server ":" test-topic " ->")
           "\"test-data\""])
        out-string))))

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

