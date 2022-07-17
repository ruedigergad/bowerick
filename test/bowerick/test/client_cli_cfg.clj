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
    [bowerick.main :as main]
    [bowerick.test.test-helper :as th]
    [cli4clj.cli-tests :as cli-test]
    [clojure.test :as test]))



(def local-jms-server "ssl://127.0.0.1:42444")
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (binding [jms/*trust-store-file* "test/cfg/blah_broker.ts"
                         jms/*trust-store-password* "b4zZ0nK"
                         jms/*key-store-file* "test/cfg/blah_broker.ks"
                         jms/*key-store-password* "f00BAR"]
                 (th/start-test-broker local-jms-server))]
    (t)
    (jms/stop broker)))

(test/use-fixtures :each test-with-broker)



(test/deftest dummy-send-test
  (let [test-cmd-input [(str "send " local-jms-server ":" test-topic " \"test-data\"")]
        out-string (cli-test/test-cli-stdout #(main/run-cli-app "-c" "-o" "-f" "test/cfg/bowerick_test_config.cfg") test-cmd-input)]
    (test/is
      (=
        (cli-test/expected-string
          [(str "Sending: " local-jms-server ":" test-topic " <-")
           "\"test-data\""
           "Closing bowerick.jms.ProducerWrapper for ssl://127.0.0.1:42444:/topic/testtopic.foo ..."])
        out-string))))

(test/deftest dummy-receive-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)]
        out-string (cli-test/test-cli-stdout #(main/run-cli-app "-c" "-o" "-f" "test/cfg/bowerick_test_config.cfg") test-cmd-input)]
    (test/is
      (=
        (cli-test/expected-string
          [(str "Set up consumer for: " local-jms-server ":" test-topic)
           "Closing bowerick.jms.ConsumerWrapper for ssl://127.0.0.1:42444:/topic/testtopic.foo ..."])
        out-string))))

(test/deftest simple-send-receive-test
  (let [test-cmd-input [(str "receive " local-jms-server ":" test-topic)
                        (str "send " local-jms-server ":" test-topic " \"test-data\"")
                        "_sleep 300"]
        out-string (cli-test/test-cli-stdout #(main/run-cli-app "-c" "-o" "-f" "test/cfg/bowerick_test_config.cfg") test-cmd-input)]
    (test/is
      (=
        (cli-test/expected-string
          [(str "Set up consumer for: " local-jms-server ":" test-topic)
           (str "Sending: " local-jms-server ":" test-topic " <-")
           "\"test-data\""
           (str "Received: " local-jms-server ":" test-topic " ->")
           "\"test-data\""
           "Closing bowerick.jms.ProducerWrapper for ssl://127.0.0.1:42444:/topic/testtopic.foo ..."
           "Closing bowerick.jms.ConsumerWrapper for ssl://127.0.0.1:42444:/topic/testtopic.foo ..."])
        out-string))))
