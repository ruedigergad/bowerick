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
    [bowerick.main :as main]
    [bowerick.test.test-helper :as th]
    [cli4clj.cli-tests :as cli-test]
    [clj-assorted-utils.util :as utils]
    [clojure.string :as str]
    [clojure.test :as test]))



(def local-jms-server "tcp://127.0.0.1:1709")
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (th/start-test-broker local-jms-server)]
    (t)
    (jms/stop broker)))

(test/use-fixtures :each test-with-broker)



(test/deftest benchmark-client-test
  (let [producer (jms/create-producer local-jms-server test-topic)
        _ (utils/run-repeat (utils/executor) #(producer "test message") 500)
        sl (cli-test/string-latch ["Benchmark client started... Type \"q\" followed by <Return> to quit: "
                          ["Type \"q\" followed by <Return> to quit: " (fn [_] (utils/sleep 2000))]])
        out-string (cli-test/test-cli-stdout #(main/run-cli-app "-u" local-jms-server "-D" test-topic "-B") ["x" "q"] sl)]
    (test/is
      (=
        "Data instances per second: 2"
        (-> out-string (str/split #"\n") last)))))

