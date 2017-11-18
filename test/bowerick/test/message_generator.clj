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
    [bowerick.test.test-helper :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.test :refer :all]))



(def local-jms-server "tcp://127.0.0.1:42424")
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (start-test-broker local-jms-server)]
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
        gen (txt-file-generator producer delay-fn "test/data/csv_input_test_file.txt" #"[\n,]")
        consumer (create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (await-flag flag)
    (is (= ["1" "2" "3" "a" "b" "4" "5" "6" "7"] @received))
    (close producer)
    (close consumer)))

(deftest txt-file-line-generator-single-test
  (let [producer (create-producer local-jms-server test-topic 1)
        received (atom [])
        flag (prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [s (String. obj)]
                       (swap! received conj s)
                       (if (= s "4,5,6,7")
                         (set-flag flag))))
        gen (txt-file-line-generator producer delay-fn "test/data/csv_input_test_file.txt")
        consumer (create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (await-flag flag)
    (is (= ["1,2,3" "a,b" "4,5,6,7"] @received))
    (close producer)
    (close consumer)))

(deftest binary-file-generator-pcap-single-test
  (let [producer (create-producer local-jms-server test-topic 1)
        received-sizes (atom [])
        flag (prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [size (alength obj)]
                       (swap! received-sizes conj size)
                       (if (= size 80)
                         (set-flag flag))))
        initial-offset 24
        length-field-offset 8
        length-field-size 4
        header-size 16
        gen (binary-file-generator producer
                                   delay-fn
                                   "test/data/binary_pcap_data_input_test.pcap"
                                   initial-offset
                                   length-field-offset
                                   length-field-size
                                   header-size)
        consumer (create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (await-flag flag)
    (is (= [229 72 252 72 443 80] @received-sizes))
    (close producer)
    (close consumer)))

(deftest pcap-file-generator-single-test
  (let [producer (create-producer local-jms-server test-topic 1)
        received-sizes (atom [])
        flag (prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [size (alength obj)]
                       (swap! received-sizes conj size)
                       (if (= size 80)
                         (set-flag flag))))
        gen (pcap-file-generator producer delay-fn "test/data/binary_pcap_data_input_test.pcap")
        consumer (create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (await-flag flag)
    (is (= [229 72 252 72 443 80] @received-sizes))
    (close producer)
    (close consumer)))

(deftest create-pcap-file-generator-with-string-args-single-test
  (let [producer (create-producer local-jms-server test-topic 1)
        received-sizes (atom [])
        flag (prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [size (alength obj)]
                       (swap! received-sizes conj size)
                       (if (= size 80)
                         (set-flag flag))))
        gen (create-message-generator producer delay-fn "pcap-file" "test/data/binary_pcap_data_input_test.pcap")
        consumer (create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (await-flag flag)
    (is (= [229 72 252 72 443 80] @received-sizes))
    (close producer)
    (close consumer)))

(deftest create-binary-file-generator-pcap-with-string-args-single-test
  (let [producer (create-producer local-jms-server test-topic 1)
        received-sizes (atom [])
        flag (prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [size (alength obj)]
                       (swap! received-sizes conj size)
                       (if (= size 80)
                         (set-flag flag))))
        initial-offset 24
        length-field-offset 8
        length-field-size 4
        header-size 16
        gen (create-message-generator
              producer
              delay-fn
              "binary-file"
              "[\"test/data/binary_pcap_data_input_test.pcap\" 24 8 4 16]")
        consumer (create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (await-flag flag)
    (is (= [229 72 252 72 443 80] @received-sizes))
    (close producer)
    (close consumer)))

(deftest create-txt-file-line-generator-with-string-args-single-test
  (let [producer (create-producer local-jms-server test-topic 1)
        received (atom [])
        flag (prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [s (String. obj)]
                       (swap! received conj s)
                       (if (= s "4,5,6,7")
                         (set-flag flag))))
        gen (create-message-generator producer delay-fn "txt-file-line" "test/data/csv_input_test_file.txt")
        consumer (create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (await-flag flag)
    (is (= ["1,2,3" "a,b" "4,5,6,7"] @received))
    (close producer)
    (close consumer)))

(deftest create-txt-file-generator-per-csv-with-string-args-single-test
  (let [producer (create-producer local-jms-server test-topic 1)
        received (atom [])
        flag (prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [s (String. obj)]
                       (swap! received conj s)
                       (if (= s "7")
                         (set-flag flag))))
        gen (create-message-generator
              producer
              delay-fn
              "txt-file"
              "[\"test/data/csv_input_test_file.txt\" #\"[\\n,]\"]")
        consumer (create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (await-flag flag)
    (is (= ["1" "2" "3" "a" "b" "4" "5" "6" "7"] @received))
    (close producer)
    (close consumer)))

(deftest create-custom-fn-generator-producer-return-test
  (let [gen-fn (create-message-generator
                 identity
                 nil
                 "custom-fn"
                 "test/data/custom-generator-fn-return-producer-value.txt")]
    (is (= "producer return value" (gen-fn)))))

(deftest create-custom-fn-generator-delay-fn-return-test
  (let [gen-fn (create-message-generator
                 nil
                 identity
                 "custom-fn"
                 "test/data/custom-generator-fn-return-delay-fn-value.txt")]
    (is (= "delay-fn return value" (gen-fn)))))

