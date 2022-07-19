;;;
;;;   Copyright 2021 Ruediger Gad
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Tests for client consumers"}  
  bowerick.test.client-consumer
  (:require
    [bowerick.jms :as jms]
    [bowerick.main :as main]
    [bowerick.test.test-helper :as th]
    [cli4clj.cli-tests :as cli-test]
    [clj-assorted-utils.util :as utils]
    [clojure.string :as str]
    [clojure.test :as test])
  (:import
    (java.io ByteArrayOutputStream PrintStream)))



(def local-jms-server "tcp://127.0.0.1:1657")
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (th/start-test-broker local-jms-server)]
    (t)
    (jms/stop broker)))

(test/use-fixtures :each test-with-broker)



(test/deftest in-line-consumer-test
  (let [producer (jms/create-producer local-jms-server test-topic)
        sl (cli-test/string-latch
            [["Consumer client started... Type \"q\" followed by <Return> to quit: "
              (fn [_] (producer "Test Message") (utils/sleep 1000))]
             ["Type \"q\" followed by <Return> to quit: " (fn [_] (utils/sleep 1000))]])
        out-string (cli-test/test-cli-stdout #(main/run-cli-app "-u" local-jms-server "-D" test-topic "-C" "(fn [m _] (println \"\nIn-line consumer:\" m))") ["x" "q"] sl)]
    (test/is
      (=
        "In-line consumer: Test Message"
        ((str/split out-string #"\n") 3)))))

(test/deftest custom-clj-consumer-test
  (let [producer (jms/create-producer local-jms-server test-topic)
        sl (cli-test/string-latch
             [["Consumer client started... Type \"q\" followed by <Return> to quit: "
               (fn [_] (producer "Test Message") (utils/sleep 1000))]
              ["Type \"q\" followed by <Return> to quit: " (fn [_] (utils/sleep 1000))]])
        out-string (cli-test/test-cli-stdout #(main/run-cli-app "-u" local-jms-server "-D" test-topic "-C" "test/data/custom-clj-consumer.clj") ["x" "q"] sl)]
    (test/is
      (=
        "Custom clj consumer: Test Message"
        ((str/split out-string #"\n") 3)))))

(test/deftest custom-java-consumer-test
  (let [producer (jms/create-producer local-jms-server test-topic)
        sl (cli-test/string-latch
             [["Consumer client started... Type \"q\" followed by <Return> to quit: "
                (fn [_] (producer "Test Message") (utils/sleep 1000))]
              ["Type \"q\" followed by <Return> to quit: " (fn [_] (utils/sleep 1000))]])
        bs (ByteArrayOutputStream.)
        ps (PrintStream. bs)
        out System/out
        _ (System/setOut ps)
        _ (cli-test/test-cli-stdout #(main/run-cli-app "-u" local-jms-server "-D" test-topic "-C" "target/classes/ExampleJavaConsumer.class") ["x" "q"] sl)]
    (.flush ps)
    (System/setOut out)
    (test/is
      (=
        "Example Java consumer: Test Message\n"
        (.toString bs)))))
