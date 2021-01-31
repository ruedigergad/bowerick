;;;
;;;   Copyright 2021 Ruediger Gad
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Tests for the client CLI with a provided config file"}  
  bowerick.test.client-cli-cfg
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



(def local-jms-server "ssl://127.0.0.1:42425")
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (binding [jms/*trust-store-file* "test/cfg/blah_broker.ts"
                         jms/*trust-store-password* "b4zZ0nK"
                         jms/*key-store-file* "test/cfg/blah_broker.ks"
                         jms/*key-store-password* "f00BAR"]
                 (start-test-broker local-jms-server))]
    (t)
    (jms/stop broker)))

(use-fixtures :each test-with-broker)



(deftest dummy-send-test
  (let [test-cmd-input [(str "send " local-jms-server ":" test-topic " \"test-data\"")]
        out-string (test-cli-stdout #(-main "-c" "-o" "-C" "test/cfg/bowerick_test_config.cfg") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Sending: " local-jms-server ":" test-topic " <-")
           "\"test-data\""
           "Closing bowerick.jms.ProducerWrapper for ssl://127.0.0.1:42425:/topic/testtopic.foo ..."])
        out-string))))

(deftest dummy-receive-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)]
        out-string (test-cli-stdout #(-main "-c" "-o" "-C" "test/cfg/bowerick_test_config.cfg") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Set up consumer for: " local-jms-server ":" test-topic)
           "Closing bowerick.jms.ConsumerWrapper for ssl://127.0.0.1:42425:/topic/testtopic.foo ..."])
        out-string))))

(deftest simple-send-receive-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)
                        (str "send " local-jms-server ":" test-topic " \"test-data\"")
                        "_sleep 300"]
        out-string (test-cli-stdout #(-main "-c" "-o" "-C" "test/cfg/bowerick_test_config.cfg") test-cmd-input)]
    (is
      (=
        (expected-string
          [(str "Set up consumer for: " local-jms-server ":" test-topic)
           (str "Sending: " local-jms-server ":" test-topic " <-")
           "\"test-data\""
           (str "Received: " local-jms-server ":" test-topic " ->")
           "\"test-data\""
           "Closing bowerick.jms.ProducerWrapper for ssl://127.0.0.1:42425:/topic/testtopic.foo ..."
           "Closing bowerick.jms.ConsumerWrapper for ssl://127.0.0.1:42425:/topic/testtopic.foo ..."])
        out-string))))

