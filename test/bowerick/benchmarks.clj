;;;
;;;   Copyright 2014, University of Applied Sciences Frankfurt am Main
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Simple Benchmarks"}  
  bowerick.benchmarks
  (:require
    [bowerick.jms :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.test :refer :all]
    [criterium [core :as cc]]))

(def ^:dynamic *local-jms-server* "tcp://127.0.0.1:42424")
(def test-topic "/topic/testtopic.foo")

(defn benchmark-fixture [t]
  (let [broker (start-broker *local-jms-server*)]
    (t)
    (.stop broker)))

(use-fixtures :each benchmark-fixture)

(deftest simple-string-transmission-benchmark
  (let [producer (create-producer *local-jms-server* test-topic)
        consume-fn (fn [_])
        consumer (create-consumer *local-jms-server* test-topic consume-fn)]
    (cc/with-progress-reporting
      (cc/quick-bench
        (producer "foo-string")))
    (close producer)
    (close consumer)))

