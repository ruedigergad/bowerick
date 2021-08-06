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
    [bowerick.main :refer :all]
    [bowerick.test.test-helper :refer :all]
    [cli4clj.cli-tests :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.string :as str]
    [clojure.test :refer :all]))



(def local-jms-server "tcp://127.0.0.1:1657")
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (start-test-broker local-jms-server)]
    (t)
    (jms/stop broker)))

(use-fixtures :each test-with-broker)



(deftest in-line-consumer-test
  (let [producer (jms/create-producer local-jms-server test-topic) 
        sl (string-latch [["Consumer client started... Type \"q\" followed by <Return> to quit: "
                           (fn [_] (producer "Test Message"))]
                          ["Type \"q\" followed by <Return> to quit: " (fn [_] (sleep 1000))]]) 
        out-string (test-cli-stdout #(run-cli-app "-u" local-jms-server "-D" test-topic "-C" "(fn [m _] (println \"\nIn-line consumer:\" m))") ["x" "q"] sl)]
    (is
      (=
        "In-line consumer: Test Message"
        (last (str/split out-string #"\n"))))))

(deftest custom-clj-consumer-test
  (let [producer (jms/create-producer local-jms-server test-topic) 
        sl (string-latch [["Consumer client started... Type \"q\" followed by <Return> to quit: "
                           (fn [_] (producer "Test Message"))]
                          ["Type \"q\" followed by <Return> to quit: " (fn [_] (sleep 1000))]]) 
        out-string (test-cli-stdout #(run-cli-app "-u" local-jms-server "-D" test-topic "-C" "test/data/custom-clj-consumer.clj") ["x" "q"] sl)]
    (is
      (=
        "Custom clj consumer: Test Message"
        (last (str/split out-string #"\n"))))))

