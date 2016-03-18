;;;
;;;   Copyright 2014, University of Applied Sciences Frankfurt am Main
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
   :methods [[connectConsumer [String bowerick.JmsConsumerCallback] bowerick.Closable]
             [createProducer [String] bowerick.JmsProducer]
             [startEmbeddedBroker [] void]
             [stopEmbeddedBroker [] void]]
   :state state)
  (:require
    [bowerick.jms :refer :all])
  (:import
    (bowerick Closable JmsConsumerCallback JmsController JmsProducer)))

(defn -init [jms-url]
  [[] {:jms-url jms-url :broker (ref nil)}])

(defn -createProducer [^JmsController this topic-identifier]
  (let [producer (create-producer (:jms-url (.state this)) topic-identifier)]
    (proxy [JmsProducer] []
      (close []
        (close producer))
      (sendObject [obj]
        (producer obj)))))

(defn -connectConsumer [^JmsController this topic-identifier ^JmsConsumerCallback consumer-cb]
  (let [consumer (create-consumer
                   (:jms-url (.state this))
                   topic-identifier
                   (fn [obj]
                     (.processObject consumer-cb obj)))]
    (proxy [Closable] []
      (close []
        (close consumer)))))

(defn -startEmbeddedBroker [^JmsController this]
  (let [broker-ref (:broker (.state this))]
    (if (nil? @broker-ref)
      (let [brkr (start-broker (:jms-url (.state this)))]
        (dosync (ref-set broker-ref brkr))))))

(defn -stopEmbeddedBroker [^JmsController this]
  (let [broker-ref (:broker (.state this))]
    (when (not (nil? @broker-ref))
      (.stop @broker-ref)
      (dosync (ref-set broker-ref nil)))))

