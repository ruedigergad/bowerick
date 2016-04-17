;;;
;;;   Copyright 2016, Ruediger Gad
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Tests for JMS broker interaction"}  
  bowerick.test.broker
  (:require
    [bowerick.jms :refer :all]
    [bowerick.test.jms-test-base :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.test :refer :all] clojure.test))

(deftest get-destinations-test
  (let [brkr (start-broker *local-jms-server*)
        _ (init-endpoint *local-jms-server* test-topic)
        destinations-with-empty (get-destinations brkr)
        destinations-non-empty (get-destinations brkr false)]
    (is
      (=
        ["/topic/ActiveMQ.Advisory.Connection" "/topic/ActiveMQ.Advisory.MasterBroker"]
        destinations-with-empty))
    (is (= [] destinations-non-empty))
    (.stop brkr)))

