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
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (start-broker local-jms-server)]
    (t)
    (stop broker)))

(use-fixtures :each test-with-broker)



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
                        (str "send " local-jms-server ":" test-topic " \"test-data\"")]
        out-string (test-cli-stdout (fn [] (-main "-c") (sleep 200)) test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Set up consumer for: " local-jms-server ":" test-topic)
           (str "Sending: " local-jms-server ":" test-topic " <-")
           "\"test-data\""
           (str "Received: " local-jms-server ":" test-topic " ->")
           "\"test-data\""])
        out-string))))

