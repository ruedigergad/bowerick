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
    [bowerick.jms :as jms]
    [bowerick.message-generator :as msg-gen]
    [bowerick.test.test-helper :as th]
    [clj-assorted-utils.util :as utils]
    [clojure.test :as test]))



(def local-jms-server "tcp://127.0.0.1:31313")
(def test-topic "/topic/testtopic.foo")

(defn test-with-broker [t]
  (let [broker (th/start-test-broker local-jms-server)]
    (t)
    (jms/stop broker)))

(test/use-fixtures :each test-with-broker)



(test/deftest list-message-gen-tests
  (test/is
   (=
    (msg-gen/list-message-gen)
    '("binary-file", "custom-fn", "heart4family", "hello-world", "pcap-file", "txt-file", "txt-file-line", "yin-yang"))))

(test/deftest txt-file-generator-per-line-single-test
  (let [producer (jms/create-producer local-jms-server test-topic 1)
        received (atom [])
        flag (utils/prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [s (String. obj)]
                       (swap! received conj s)
                       (when (= s "4,5,6,7")
                         (utils/set-flag flag))))
        gen (msg-gen/txt-file-generator producer delay-fn "file:./test/data/csv_input_test_file.txt" #"\n")
        consumer (jms/create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (utils/await-flag flag)
    (test/is (= ["1,2,3" "a,b" "4,5,6,7"] @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest txt-file-generator-per-csv-single-test
  (let [producer (jms/create-producer local-jms-server test-topic 1)
        received (atom [])
        flag (utils/prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [s (String. obj)]
                       (swap! received conj s)
                       (when (= s "7")
                         (utils/set-flag flag))))
        gen (msg-gen/txt-file-generator producer delay-fn "test/data/csv_input_test_file.txt" #"[\n,]")
        consumer (jms/create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (utils/await-flag flag)
    (test/is (= ["1" "2" "3" "a" "b" "4" "5" "6" "7"] @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest txt-file-line-generator-single-test
  (let [producer (jms/create-producer local-jms-server test-topic 1)
        received (atom [])
        flag (utils/prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [s (String. obj)]
                       (swap! received conj s)
                       (when (= s "4,5,6,7")
                         (utils/set-flag flag))))
        gen (msg-gen/txt-file-line-generator producer delay-fn "test/data/csv_input_test_file.txt")
        consumer (jms/create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (utils/await-flag flag)
    (test/is (= ["1,2,3" "a,b" "4,5,6,7"] @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest binary-file-generator-pcap-single-test
  (let [producer (jms/create-producer local-jms-server test-topic 1)
        received-sizes (atom [])
        flag (utils/prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [size (alength obj)]
                       (swap! received-sizes conj size)
                       (when (= size 80)
                         (utils/set-flag flag))))
        initial-offset 24
        length-field-offset 8
        length-field-size 4
        header-size 16
        gen (msg-gen/binary-file-generator producer
                                   delay-fn
                                   "test/data/binary_pcap_data_input_test.pcap"
                                   initial-offset
                                   length-field-offset
                                   length-field-size
                                   header-size)
        consumer (jms/create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (utils/await-flag flag)
    (test/is (= [229 72 252 72 443 80] @received-sizes))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest pcap-file-generator-single-test
  (let [producer (jms/create-producer local-jms-server test-topic 1)
        received-sizes (atom [])
        flag (utils/prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [size (alength obj)]
                       (swap! received-sizes conj size)
                       (when (= size 80)
                         (utils/set-flag flag))))
        gen (msg-gen/pcap-file-generator producer delay-fn "test/data/binary_pcap_data_input_test.pcap")
        consumer (jms/create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (utils/await-flag flag)
    (test/is (= [229 72 252 72 443 80] @received-sizes))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest create-pcap-file-generator-with-string-args-single-test
  (let [producer (jms/create-producer local-jms-server test-topic 1)
        received-sizes (atom [])
        flag (utils/prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [size (alength obj)]
                       (swap! received-sizes conj size)
                       (when (= size 80)
                         (utils/set-flag flag))))
        gen (msg-gen/create-message-gen producer delay-fn "pcap-file" "test/data/binary_pcap_data_input_test.pcap")
        consumer (jms/create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (utils/await-flag flag)
    (test/is (= [229 72 252 72 443 80] @received-sizes))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest create-binary-file-generator-pcap-with-string-args-single-test
  (let [producer (jms/create-producer local-jms-server test-topic 1)
        received-sizes (atom [])
        flag (utils/prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [size (alength obj)]
                       (swap! received-sizes conj size)
                       (when (= size 80)
                         (utils/set-flag flag))))
        initial-offset 24
        length-field-offset 8
        length-field-size 4
        header-size 16
        gen (msg-gen/create-message-gen
              producer
              delay-fn
              "binary-file"
              (str "[\"test/data/binary_pcap_data_input_test.pcap\" " initial-offset " " length-field-offset " " length-field-size " " header-size "]"))
        consumer (jms/create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (utils/await-flag flag)
    (test/is (= [229 72 252 72 443 80] @received-sizes))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest create-txt-file-line-generator-with-string-args-single-test
  (let [producer (jms/create-producer local-jms-server test-topic 1)
        received (atom [])
        flag (utils/prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [s (String. obj)]
                       (swap! received conj s)
                       (when (= s "4,5,6,7")
                         (utils/set-flag flag))))
        gen (msg-gen/create-message-gen producer delay-fn "txt-file-line" "test/data/csv_input_test_file.txt")
        consumer (jms/create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (utils/await-flag flag)
    (test/is (= ["1,2,3" "a,b" "4,5,6,7"] @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest create-txt-file-generator-per-csv-with-string-args-single-test
  (let [producer (jms/create-producer local-jms-server test-topic 1)
        received (atom [])
        flag (utils/prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [s (String. obj)]
                       (swap! received conj s)
                       (when (= s "7")
                         (utils/set-flag flag))))
        gen (msg-gen/create-message-gen
              producer
              delay-fn
              "txt-file"
              "[\"test/data/csv_input_test_file.txt\" #\"[\\n,]\"]")
        consumer (jms/create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (utils/await-flag flag)
    (test/is (= ["1" "2" "3" "a" "b" "4" "5" "6" "7"] @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest create-custom-fn-generator-producer-return-test
  (let [gen-fn (msg-gen/create-message-gen
                 identity
                 nil
                 "custom-fn"
                 "test/data/custom-generator-fn-return-producer-value.clj")]
    (test/is (= "producer return value" (gen-fn)))))

(test/deftest create-custom-fn-generator-delay-fn-return-test
  (let [gen-fn (msg-gen/create-message-gen
                 nil
                 identity
                 "custom-fn"
                 "test/data/custom-generator-fn-return-delay-fn-value.clj")]
    (test/is (= "delay-fn return value" (gen-fn)))))

(test/deftest custom-fn-generator-single-test
  (let [producer (jms/create-producer local-jms-server test-topic 1)
        received (atom nil)
        flag (utils/prepare-flag)
        delay-fn #(utils/sleep 100)
        consume-fn (fn [obj]
                     (let [s (String. obj)]
                       (reset! received s)
                       (utils/set-flag flag)))
        gen (msg-gen/create-message-gen producer delay-fn "custom-fn" "test/data/custom-generator-fn-hello.clj")
        consumer (jms/create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (utils/await-flag flag)
    (test/is (= "Hello custom-fn generator!" @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest custom-fn-generator-java-class-single-test
  (let [producer (jms/create-producer local-jms-server test-topic 1)
        received (atom nil)
        flag (utils/prepare-flag)
        delay-fn #(utils/sleep 100)
        consume-fn (fn [obj]
                     (let [s (String. obj)]
                       (reset! received s)
                       (utils/set-flag flag)))
        gen (msg-gen/create-message-gen producer delay-fn "custom-fn" "./target/classes/HelloWorldMessageGenerator.class")
        consumer (jms/create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (utils/await-flag flag)
    (test/is (= "Hello World from Java" @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest hello-world-generator-single-test
  (let [producer (jms/create-producer local-jms-server test-topic 1)
        received (atom nil)
        flag (utils/prepare-flag)
        delay-fn #()
        consume-fn (fn [obj]
                     (let [s (String. obj)]
                       (reset! received s)
                       (utils/set-flag flag)))
        gen (msg-gen/create-message-gen producer delay-fn "hello-world" nil)
        consumer (jms/create-consumer local-jms-server test-topic consume-fn)]
    (gen)
    (utils/await-flag flag)
    (test/is (= "hello world" @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest heart4family-single-test
  (let [received (atom nil)
        flag (utils/prepare-flag)
        delay-fn #(utils/sleep 100)
        consume-fn (fn [obj]
                     (when (not (utils/flag-set? flag))
                       (reset! received obj)
                       (utils/set-flag flag)))
        consumer (jms/create-json-consumer local-jms-server test-topic consume-fn)
        producer (jms/create-producer local-jms-server test-topic 1)
        gen (msg-gen/create-message-gen producer delay-fn "heart4family" nil)]
    (utils/run-once (utils/executor) gen 0)
    (utils/await-flag flag)
    (test/is (= {"x" -4.408022441584257E-48, "y" -1.3499999999999999, "z" 0.0} @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest heart4family-sequence-test
  (let [received (atom [])
        flag (utils/prepare-flag)
        delay-fn #(utils/sleep 100)
        consume-fn (fn [obj]
                     (if (> (count @received) 5)
                       (utils/set-flag flag)
                       (swap! received conj obj)))
        consumer (jms/create-json-consumer local-jms-server test-topic consume-fn)
        producer (jms/create-producer local-jms-server test-topic 1)
        gen (msg-gen/create-message-gen producer delay-fn "heart4family" nil)]
    (utils/run-once (utils/executor) gen 0)
    (utils/await-flag flag)
    (test/is (=
          [{"x" -4.408022441584257E-48, "y" -1.3499999999999999, "z" 0.0}
           {"x" -0.0010096558118889592, "y" -1.336958745018994, "z" 0.0}
           {"x" -0.008009317985740547, "y" -1.2982721803501354, "z" 0.0}
           {"x" -0.02665306180052213, "y" -1.2352132454498113, "z" 0.0}
           {"x" -0.06194022621392171, "y" -1.1497785376674912, "z" 0.0}
           {"x" -0.11792999589542943, "y" -1.044514139594933, "z" 0.0}]
          @received))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest yin-yang-single-test
  (let [received (atom nil)
        flag (utils/prepare-flag)
        delay-fn #(utils/sleep 100)
        consume-fn (fn [obj]
                     (when (not (utils/flag-set? flag))
                       (reset! received obj)
                       (utils/set-flag flag)))
        consumer (jms/create-json-consumer local-jms-server test-topic consume-fn)
        producer (jms/create-producer local-jms-server test-topic 1)
        gen (msg-gen/create-message-gen producer delay-fn "yin-yang" nil)]
    (utils/run-once (utils/executor) gen 0)
    (utils/await-flag flag)
    (test/is (= 203 (count @received)))
    (test/is (= {"x" 1.0, "y" 0.0, "z" 0.0} (-> @received first (select-keys ["x" "y" "z"]))))
    (jms/close producer)
    (jms/close consumer)))

(test/deftest yin-yang-sequence-test
  (let [received (atom [])
        flag (utils/prepare-flag)
        delay-fn #(utils/sleep 100)
        consume-fn (fn [obj]
                     (if (> (count @received) 5)
                       (utils/set-flag flag)
                       (swap! received conj obj)))
        consumer (jms/create-json-consumer local-jms-server test-topic consume-fn)
        producer (jms/create-producer local-jms-server test-topic 1)
        gen (msg-gen/create-message-gen producer delay-fn "yin-yang" nil)]
    (utils/run-repeat (utils/executor) gen 100)
    (utils/await-flag flag)
    (test/is (every? #(= 203 (count %)) @received))
    (jms/close producer)
    (jms/close consumer)))
