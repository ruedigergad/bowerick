;;;
;;;   Copyright 2017, Ruediger Gad
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns 
  ^{:author "Ruediger Gad",
    :doc "Tests for message generation"}  
  bowerick.test.message-generator
  (:require
    [bowerick.jms :refer :all]
    [bowerick.message-generator :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.test :refer :all]))



(def local-jms-server "tcp://127.0.0.1:42424")
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (start-broker local-jms-server)]
    (t)
    (stop broker)))

(use-fixtures :each test-with-broker)



(deftest txt-file-generator-per-line-single-test
  (let [producer (create-producer local-jms-server test-topic 1)
        received (atom [])
        flag (prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [s (String. obj)]
                       (swap! received conj s)
                       (if (= s "4,5,6,7")
                         (set-flag flag))))
        gen (txt-file-generator producer delay-fn "file:./test/data/csv_input_test_file.txt" #"\n")
        consumer (create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (await-flag flag)
    (is (= ["1,2,3" "a,b" "4,5,6,7"] @received))
    (close producer)
    (close consumer)))

(deftest txt-file-generator-per-csv-single-test
  (let [producer (create-producer local-jms-server test-topic 1)
        received (atom [])
        flag (prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [s (String. obj)]
                       (swap! received conj s)
                       (if (= s "7")
                         (set-flag flag))))
        gen (txt-file-generator producer delay-fn "file:./test/data/csv_input_test_file.txt" #"[\n,]")
        consumer (create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (await-flag flag)
    (is (= ["1" "2" "3" "a" "b" "4" "5" "6" "7"] @received))
    (close producer)
    (close consumer)))

