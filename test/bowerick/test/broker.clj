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
    [bowerick.test.test-helper :refer :all]
    [clj-assorted-utils.util :refer :all]
    [clojure.test :refer :all]))



(def local-jms-server "tcp://127.0.0.1:42424")
(def test-topic "/topic/testtopic.foo")
(def broker-management-command-topic "/topic/bowerick.broker.management.command")
(def broker-management-reply-topic "/topic/bowerick.broker.management.reply")



(deftest get-destinations-test
  (let [brkr (start-test-broker local-jms-server)
        destinations-with-empty (get-destinations brkr)
        destinations-non-empty (get-destinations brkr false)]
    (is
      (=
        (sort
          ["/topic/ActiveMQ.Advisory.Connection"
           "/topic/ActiveMQ.Advisory.Consumer.Topic.bowerick.broker.management.command"
           "/topic/ActiveMQ.Advisory.MasterBroker"
           "/topic/ActiveMQ.Advisory.Producer.Topic.bowerick.broker.management.reply"
           "/topic/ActiveMQ.Advisory.Topic"
           "/topic/bowerick.broker.management.command"
           "/topic/bowerick.broker.management.reply"])
        destinations-with-empty))
    (is (= '("/topic/bowerick.broker.management.reply") destinations-non-empty))
    (stop brkr)))

(deftest get-destinations-with-producer-test
  (let [brkr (start-test-broker local-jms-server)
        producer (create-single-producer local-jms-server test-topic)
        destinations-with-empty (get-destinations brkr)
        destinations-non-empty (get-destinations brkr false)]
    (is
      (=
        (sort
          ["/topic/ActiveMQ.Advisory.Connection"
           "/topic/ActiveMQ.Advisory.Consumer.Topic.bowerick.broker.management.command"
           "/topic/ActiveMQ.Advisory.MasterBroker"
           "/topic/ActiveMQ.Advisory.Producer.Topic.bowerick.broker.management.reply"
           "/topic/ActiveMQ.Advisory.Producer.Topic.testtopic.foo"
           "/topic/ActiveMQ.Advisory.Topic"
           "/topic/bowerick.broker.management.command"
           "/topic/bowerick.broker.management.reply"
           "/topic/testtopic.foo"])
        destinations-with-empty))
    (is
      (=
        (sort
          ["/topic/bowerick.broker.management.reply" "/topic/testtopic.foo"])
        destinations-non-empty))
    (close producer)
    (stop brkr)))

(deftest get-destinations-via-jms-test
  (let [brkr (start-test-broker local-jms-server)
        ret (atom nil)
        flag (prepare-flag)
        producer (create-json-producer
                   local-jms-server
                   broker-management-command-topic)
        consumer (create-json-consumer
                   local-jms-server
                   broker-management-reply-topic
                   (fn [data]
                     (reset! ret (binding [*read-eval* false] (read-string data)))
                     (set-flag flag)))]
    (producer "get-destinations")
    (await-flag flag)
    (is (=
         (sort
           ["/topic/bowerick.broker.management.command" "/topic/bowerick.broker.management.reply"])
         @ret))
    (close producer)
    (close consumer)
    (stop brkr)))

(deftest get-all-destinations-via-jms-test
  (let [brkr (start-test-broker local-jms-server)
        ret (atom nil)
        flag (prepare-flag)
        producer (create-json-producer
                   local-jms-server
                   broker-management-command-topic)
        consumer (create-json-consumer
                   local-jms-server
                   broker-management-reply-topic
                   (fn [data]
                     (reset! ret (binding [*read-eval* false] (read-string data)))
                     (set-flag flag)))]
    (producer "get-all-destinations")
    (await-flag flag)
    (is (=
         (sort
           ["/topic/ActiveMQ.Advisory.Connection"
            "/topic/ActiveMQ.Advisory.Consumer.Topic.bowerick.broker.management.command"
            "/topic/ActiveMQ.Advisory.Consumer.Topic.bowerick.broker.management.reply"
            "/topic/ActiveMQ.Advisory.MasterBroker"
            "/topic/ActiveMQ.Advisory.Producer.Topic.bowerick.broker.management.command"
            "/topic/ActiveMQ.Advisory.Producer.Topic.bowerick.broker.management.reply"
            "/topic/ActiveMQ.Advisory.Topic"
            "/topic/bowerick.broker.management.command"
            "/topic/bowerick.broker.management.reply"])
         @ret))
    (close producer)
    (close consumer)
    (stop brkr)))

(deftest send-unknown-command-test
  (let [brkr (start-test-broker local-jms-server)
        ret (atom nil)
        flag (prepare-flag)
        producer (create-json-producer
                   local-jms-server
                   broker-management-command-topic)
        consumer (create-json-consumer
                   local-jms-server
                   broker-management-reply-topic
                   (fn [data]
                     (reset! ret data)
                     (set-flag flag)))]
    (producer "foo_unknown_command_bar")
    (await-flag flag)
    (is (=
         "ERROR: Invalid command: \"[foo_unknown_command_bar]\". Please type \"help\" to get an overview of commands."
         @ret))
    (close producer)
    (close consumer)
    (stop brkr)))

