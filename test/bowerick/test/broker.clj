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
    [bowerick.jms :as jms]
    [bowerick.test.test-helper :as th]
    [clj-assorted-utils.util :as utils]
    [clojure.test :as test]))



(def local-jms-server "tcp://127.0.0.1:12121")
(def test-topic "/topic/testtopic.foo")
(def broker-management-command-topic "/topic/bowerick.broker.management.command")
(def broker-management-reply-topic "/topic/bowerick.broker.management.reply")



(test/deftest get-destinations-test
  (let [brkr (th/start-test-broker local-jms-server)
        destinations-with-empty (jms/get-destinations brkr)
        destinations-non-empty (jms/get-destinations brkr false)]
    (test/is
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
    (test/is (= '("/topic/bowerick.broker.management.reply") destinations-non-empty))
    (jms/stop brkr)))

(test/deftest get-destinations-with-producer-test
  (let [brkr (th/start-test-broker local-jms-server)
        producer (jms/create-single-producer local-jms-server test-topic)
        destinations-with-empty (jms/get-destinations brkr)
        destinations-non-empty (jms/get-destinations brkr false)]
    (test/is
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
    (test/is
      (=
        (sort
          ["/topic/bowerick.broker.management.reply" "/topic/testtopic.foo"])
        destinations-non-empty))
    (jms/close producer)
    (jms/stop brkr)))

(test/deftest get-destinations-via-jms-test
  (let [brkr (th/start-test-broker local-jms-server)
        ret (atom nil)
        flag (utils/prepare-flag)
        producer (jms/create-json-producer
                  local-jms-server
                  broker-management-command-topic)
        consumer (jms/create-json-consumer
                  local-jms-server
                  broker-management-reply-topic
                  (fn [data]
                    (reset! ret (binding [*read-eval* false] (read-string data)))
                    (utils/set-flag flag)))]
    (producer "get-destinations")
    (utils/await-flag flag)
    (test/is (=
               (sort
                 ["/topic/bowerick.broker.management.command" "/topic/bowerick.broker.management.reply"])
                @ret))
    (jms/close producer)
    (jms/close consumer)
    (jms/stop brkr)))

(test/deftest get-all-destinations-via-jms-test
  (let [brkr (th/start-test-broker local-jms-server)
        ret (atom nil)
        flag (utils/prepare-flag)
        producer (jms/create-json-producer
                   local-jms-server
                   broker-management-command-topic)
        consumer (jms/create-json-consumer
                   local-jms-server
                   broker-management-reply-topic
                   (fn [data]
                     (reset! ret (binding [*read-eval* false] (read-string data)))
                     (utils/set-flag flag)))]
    (producer "get-all-destinations")
    (utils/await-flag flag)
    (test/is (=
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
    (jms/close producer)
    (jms/close consumer)
    (jms/stop brkr)))

(test/deftest send-unknown-command-test
  (let [brkr (th/start-test-broker local-jms-server)
        ret (atom nil)
        flag (utils/prepare-flag)
        producer (jms/create-json-producer
                   local-jms-server
                   broker-management-command-topic)
        consumer (jms/create-json-consumer
                   local-jms-server
                   broker-management-reply-topic
                   (fn [data]
                     (reset! ret data)
                     (utils/set-flag flag)))]
    (producer "foo_unknown_command_bar")
    (utils/await-flag flag)
    (test/is (=
               "ERROR: Invalid command: \"[foo_unknown_command_bar]\". Please type \"help\" to get an overview of commands."
               @ret))
    (jms/close producer)
    (jms/close consumer)
    (jms/stop brkr)))
