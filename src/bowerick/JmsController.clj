;;;
;;;   Copyright 2016, Ruediger Gad
;;;   Copyright 2014, 2015 University of Applied Sciences Frankfurt am Main
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns
  ^{:author "Ruediger Gad",
    :doc "JMS controller for ActiveMQ."} 
  bowerick.JmsController
  (:gen-class
   :init init
   :constructors {[String] []}
   :methods [[createConsumer [String bowerick.JmsConsumerCallback int] AutoCloseable]
             [createProducer [String int] bowerick.JmsProducer]
             [createJsonConsumer [String bowerick.JmsConsumerCallback int] AutoCloseable]
             [createJsonProducer [String int] bowerick.JmsProducer]
             [createCarboniteConsumer [String bowerick.JmsConsumerCallback int] AutoCloseable]
             [createCarboniteProducer [String int] bowerick.JmsProducer]
             [createCarboniteLzfConsumer [String bowerick.JmsConsumerCallback int] AutoCloseable]
             [createCarboniteLzfProducer [String int] bowerick.JmsProducer]
             [startEmbeddedBroker [] void]
             [stopEmbeddedBroker [] void]]
   :state state)
  (:require
    [bowerick.jms :refer :all])
  (:import
    (bowerick JmsConsumerCallback JmsController JmsProducer)))

(defn -init [jms-url]
  [[] {:jms-url jms-url :broker (ref nil)}])

(defn -createConsumer [this topic-identifier ^JmsConsumerCallback consumer-cb pool-size]
  (create-consumer
    (:jms-url (.state this))
    topic-identifier
    (fn [obj]
      (.processData consumer-cb obj))
    pool-size))

(defn -createProducer [this topic-identifier pool-size]
  (create-producer
    (:jms-url (.state this))
    topic-identifier
    pool-size))

(defn -createJsonConsumer [this topic-identifier ^JmsConsumerCallback consumer-cb pool-size]
  (create-consumer
    (:jms-url (.state this))
    topic-identifier
    (fn [obj]
      (.processData consumer-cb obj))
    pool-size
    cheshire.core/parse-string))

(defn -createJsonProducer [this topic-identifier pool-size]
  (create-producer
    (:jms-url (.state this))
    topic-identifier
    pool-size
    cheshire.core/generate-string))

(defn -createCarboniteProducer [this topic-identifier pool-size]
  (create-carbonite-producer
    (:jms-url (.state this))
    topic-identifier
    pool-size))

(defn -createCarboniteConsumer [this topic-identifier ^JmsConsumerCallback consumer-cb pool-size]
  (create-carbonite-consumer
    (:jms-url (.state this))
    topic-identifier
    (fn [obj]
      (.processData consumer-cb obj))
    pool-size))

(defn -createCarboniteLzfProducer [this topic-identifier pool-size]
  (create-carbonite-lzf-producer
    (:jms-url (.state this))
    topic-identifier
    pool-size))

(defn -createCarboniteLzfConsumer [this topic-identifier ^JmsConsumerCallback consumer-cb pool-size]
  (create-carbonite-lzf-consumer
    (:jms-url (.state this))
    topic-identifier
    (fn [obj]
      (.processData consumer-cb obj))
    pool-size))

(defn -startEmbeddedBroker [this]
  (let [broker-ref (:broker (.state this))]
    (if (nil? @broker-ref)
      (let [brkr (start-broker (:jms-url (.state this)))]
        (dosync (ref-set broker-ref brkr))))))

(defn -stopEmbeddedBroker [this]
  (let [broker-ref (:broker (.state this))]
    (when (not (nil? @broker-ref))
      (stop @broker-ref)
      (dosync (ref-set broker-ref nil)))))

