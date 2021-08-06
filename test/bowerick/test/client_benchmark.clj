;;;
;;;   Copyright 2021 Ruediger Gad
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Tests for benchmark client"}
  bowerick.test.client-benchmark
  (:require
    [bowerick.jms :as jms]
    [bowerick.main :refer :all]
    [bowerick.test.test-helper :refer :all]
    [cli4clj.cli-tests :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.string :as str]
    [clojure.test :refer :all]))



(def local-jms-server "tcp://127.0.0.1:1709")
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (start-test-broker local-jms-server)]
    (t)
    (jms/stop broker)))

(use-fixtures :each test-with-broker)



(deftest benchmark-client-test
  (let [producer (jms/create-producer local-jms-server test-topic)
        _ (run-repeat (executor) #(producer "test message") 500)
        sl (string-latch ["Benchmark client started... Type \"q\" followed by <Return> to quit: "
                          ["Type \"q\" followed by <Return> to quit: " (fn [_] (sleep 2000))]])
        out-string (test-cli-stdout #(run-cli-app "-u" local-jms-server "-D" test-topic "-B") ["x" "q"] sl)]
    (is
      (=
        "Data instances per second: 2"
        (-> out-string (str/split #"\n") last)))))

