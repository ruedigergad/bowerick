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
                 "test/data/custom-generator-fn-return-producer-value.clj")]
    (is (= "producer return value" (gen-fn)))))

(deftest create-custom-fn-generator-delay-fn-return-test
  (let [gen-fn (create-message-generator
                 nil
                 identity
                 "custom-fn"
                 "test/data/custom-generator-fn-return-delay-fn-value.clj")]
    (is (= "delay-fn return value" (gen-fn)))))

(deftest custom-fn-generator-single-test
  (let [producer (create-producer local-jms-server test-topic 1)
        received (atom nil)
        flag (prepare-flag)
        delay-fn #(sleep 100)
        consume-fn (fn [obj]
                     (let [s (String. obj)]
                       (reset! received s)
                       (set-flag flag)))
        gen (create-message-generator producer delay-fn "custom-fn" "test/data/custom-generator-fn-hello.clj")
        consumer (create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (await-flag flag)
    (is (= "Hello custom-fn generator!" @received))
    (close producer)
    (close consumer)))

(deftest custom-fn-generator-java-class-single-test
  (let [producer (create-producer local-jms-server test-topic 1)
        received (atom nil)
        flag (prepare-flag)
        delay-fn #(sleep 100)
        consume-fn (fn [obj]
                     (let [s (String. obj)]
                       (reset! received s)
                       (set-flag flag)))
        gen (create-message-generator producer delay-fn "custom-fn" "./target/classes/HelloWorldMessageGenerator.class")
        consumer (create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (await-flag flag)
    (is (= "Hello World from Java" @received))
    (close producer)
    (close consumer)))

(deftest hello-world-generator-single-test
  (let [producer (create-producer local-jms-server test-topic 1)
        received (atom nil)
        flag (prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [s (String. obj)]
                       (reset! received s)
                       (set-flag flag)))
        gen (create-message-generator producer delay-fn "hello-world" nil)
        consumer (create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (await-flag flag)
    (is (= "hello world" @received))
    (close producer)
    (close consumer)))

(deftest heart4family-single-test
  (let [received (atom nil)
        flag (prepare-flag)
        delay-fn #(sleep 100)
        consume-fn (fn [obj]
                     (when (not (flag-set? flag))
                       (reset! received obj)
                       (set-flag flag)))
        consumer (create-json-consumer local-jms-server test-topic consume-fn)
        producer (create-producer local-jms-server test-topic 1)
        gen (create-message-generator producer delay-fn "heart4family" nil)]
    (run-once (executor) gen 0)
    (await-flag flag)
    (is (= {"x" -4.408022441584257E-48, "y" -1.3499999999999999, "z" 0.0} @received))
    (close producer)
    (close consumer)))

(deftest heart4family-sequence-test
  (let [received (atom [])
        flag (prepare-flag)
        delay-fn #(sleep 100)
        consume-fn (fn [obj]
                     (if (> (count @received) 5)
                       (set-flag flag)
                       (swap! received conj obj)))
        consumer (create-json-consumer local-jms-server test-topic consume-fn)
        producer (create-producer local-jms-server test-topic 1)
        gen (create-message-generator producer delay-fn "heart4family" nil)]
    (run-once (executor) gen 0)
    (await-flag flag)
    (is (=
          [{"x" -4.408022441584257E-48, "y" -1.3499999999999999, "z" 0.0}
           {"x" -0.0010096558118889592, "y" -1.336958745018994, "z" 0.0}
           {"x" -0.008009317985740547, "y" -1.2982721803501354, "z" 0.0}
           {"x" -0.02665306180052213, "y" -1.2352132454498113, "z" 0.0}
           {"x" -0.06194022621392171, "y" -1.1497785376674912, "z" 0.0}
           {"x" -0.11792999589542943, "y" -1.044514139594933, "z" 0.0}]
          @received))
    (close producer)
    (close consumer)))

(deftest yin-yang-single-test
  (let [received (atom nil)
        flag (prepare-flag)
        delay-fn #(sleep 100)
        consume-fn (fn [obj]
                     (when (not (flag-set? flag))
                       (reset! received obj)
                       (set-flag flag)))
        consumer (create-json-consumer local-jms-server test-topic consume-fn)
        producer (create-producer local-jms-server test-topic 1)
        gen (create-message-generator producer delay-fn "yin-yang" nil)]
    (run-once (executor) gen 0)
    (await-flag flag)
    (is (= 203 (count @received)))
    (is (= {"x" 1.0, "y" 0.0, "z" 0.0} (-> @received first (select-keys ["x" "y" "z"]))))
    (close producer)
    (close consumer)))

(deftest yin-yang-sequence-test
  (let [received (atom [])
        flag (prepare-flag)
        delay-fn #(sleep 100)
        consume-fn (fn [obj]
                     (if (> (count @received) 5)
                       (set-flag flag)
                       (swap! received conj obj)))
        consumer (create-json-consumer local-jms-server test-topic consume-fn)
        producer (create-producer local-jms-server test-topic 1)
        gen (create-message-generator producer delay-fn "yin-yang" nil)]
    (run-repeat (executor) gen 100)
    (await-flag flag)
    (is (every? #(= 203 (count %)) @received))
    (close producer)
    (close consumer)))

