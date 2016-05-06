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
   :methods [[createConsumer [String bowerick.JmsConsumerCallback] AutoCloseable]
             [createJsonConsumer [String bowerick.JmsConsumerCallback] AutoCloseable]
             [createJsonProducer [String] bowerick.JmsProducer]
             [createProducer [String] bowerick.JmsProducer]
             [createPooledConsumer [String bowerick.JmsConsumerCallback] AutoCloseable]
             [createPooledProducer [String int] bowerick.JmsProducer]
             [createPooledCarboniteConsumer [String bowerick.JmsConsumerCallback] AutoCloseable]
             [createPooledCarboniteProducer [String int] bowerick.JmsProducer]
             [createPooledCarboniteLzfConsumer [String bowerick.JmsConsumerCallback] AutoCloseable]
             [createPooledCarboniteLzfProducer [String int] bowerick.JmsProducer]
             [startEmbeddedBroker [] void]
             [stopEmbeddedBroker [] void]]
   :state state)
  (:require
    [bowerick.jms :refer :all]
    [cheshire.core :refer :all]
    )
  (:import
    (bowerick JmsConsumerCallback JmsController JmsProducer)))

(defn -init [jms-url]
  [[] {:jms-url jms-url :broker (ref nil)}])

(defn -createConsumer [this topic-identifier ^JmsConsumerCallback consumer-cb]
  (create-consumer
    (:jms-url (.state this))
    topic-identifier
    (fn [obj]
      (.processData consumer-cb obj))))

(defn -createJsonConsumer [this topic-identifier ^JmsConsumerCallback consumer-cb]
  (create-consumer
    (:jms-url (.state this))
    topic-identifier
    (fn [obj]
      (.processData consumer-cb obj))
    parse-string))

(defn -createJsonProducer [this topic-identifier]
  (create-producer
    (:jms-url (.state this))
    topic-identifier
    generate-string))

(defn -createProducer [this topic-identifier]
  (create-producer
    (:jms-url (.state this))
    topic-identifier))

(defn -createPooledProducer [this topic-identifier pool-size]
  (create-pooled-producer
    (:jms-url (.state this))
    topic-identifier
    pool-size))

(defn -createPooledConsumer [this topic-identifier ^JmsConsumerCallback consumer-cb]
  (create-pooled-consumer
    (:jms-url (.state this))
    topic-identifier
    (fn [obj]
      (.processData consumer-cb obj))))

(defn -createPooledCarboniteProducer [this topic-identifier pool-size]
  (create-pooled-carbonite-producer
    (:jms-url (.state this))
    topic-identifier
    pool-size))

(defn -createPooledCarboniteConsumer [this topic-identifier ^JmsConsumerCallback consumer-cb]
  (create-pooled-carbonite-consumer
    (:jms-url (.state this))
    topic-identifier
    (fn [obj]
      (.processData consumer-cb obj))))

(defn -createPooledCarboniteLzfProducer [this topic-identifier pool-size]
  (create-pooled-carbonite-lzf-producer
    (:jms-url (.state this))
    topic-identifier
    pool-size))

(defn -createPooledCarboniteLzfConsumer [this topic-identifier ^JmsConsumerCallback consumer-cb]
  (create-pooled-carbonite-lzf-consumer
    (:jms-url (.state this))
    topic-identifier
    (fn [obj]
      (.processData consumer-cb obj))))

(defn -startEmbeddedBroker [this]
  (let [broker-ref (:broker (.state this))]
    (if (nil? @broker-ref)
      (let [brkr (start-broker (:jms-url (.state this)))]
        (dosync (ref-set broker-ref brkr))))))

(defn -stopEmbeddedBroker [this]
  (let [broker-ref (:broker (.state this))]
    (when (not (nil? @broker-ref))
      (.stop @broker-ref)
      (dosync (ref-set broker-ref nil)))))

