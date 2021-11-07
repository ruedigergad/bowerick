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
   :constructors {[Object] []}
   :methods [^:static [configure [String] void]
             ^:static [createConsumer [String String bowerick.JmsConsumerCallback int] AutoCloseable]
             ^:static [createProducer [String String int] bowerick.JmsProducer]
             ^:static [createJsonConsumer [String String bowerick.JmsConsumerCallback int] AutoCloseable]
             ^:static [createJsonProducer [String String int] bowerick.JmsProducer]
             ^:static [createCarboniteConsumer [String String bowerick.JmsConsumerCallback int] AutoCloseable]
             ^:static [createCarboniteProducer [String String int] bowerick.JmsProducer]
             ^:static [createCarboniteLzfConsumer [String String bowerick.JmsConsumerCallback int] AutoCloseable]
             ^:static [createCarboniteLzfProducer [String String int] bowerick.JmsProducer]
             [startEmbeddedBroker [] void]
             [stopEmbeddedBroker [] void]]
   :state state)
  (:require
    [bowerick.jms :as jms]
    [cheshire.core :as cheshire]
    [clj-assorted-utils.util :refer :all])
  (:import
    (bowerick JmsConsumerCallback JmsController JmsProducer)))

(defn -init [broker-url]
  [[] {:broker-url broker-url :broker (ref nil)}])

(defn -configure [config-file-path]
  (let [cfg-file-data (if (is-file? config-file-path)
                        (cheshire/parse-string (slurp config-file-path))
                        {})
        jms-cfg (cfg-file-data "jms")]
    (when (not (nil? jms-cfg))
      (println "Applying jms configuration from config file:" config-file-path)
      (doseq [[k v] jms-cfg]
        (alter-var-root (ns-resolve 'bowerick.jms (symbol k)) (fn [_ x] x) v)))))

(defn -createConsumer [broker-url destination-description ^JmsConsumerCallback consumer-cb pool-size]
  (jms/create-consumer
    broker-url
    destination-description
    (fn [data msg-hdr]
      (.processData consumer-cb data msg-hdr))
    pool-size))

(defn -createProducer [broker-url destination-description pool-size]
  (jms/create-producer
    broker-url
    destination-description
    pool-size))

(defn -createJsonConsumer [broker-url destination-description ^JmsConsumerCallback consumer-cb pool-size]
  (jms/create-json-consumer
    broker-url
    destination-description
    (fn [data msg-hdr]
      (.processData consumer-cb data msg-hdr))
    pool-size))

(defn -createJsonProducer [broker-url destination-description pool-size]
  (jms/create-json-producer
    broker-url
    destination-description
    pool-size))

(defn -createCarboniteProducer [broker-url destination-description pool-size]
  (jms/create-carbonite-producer
    broker-url
    destination-description
    pool-size))

(defn -createCarboniteConsumer [broker-url destination-description ^JmsConsumerCallback consumer-cb pool-size]
  (jms/create-carbonite-consumer
    broker-url
    destination-description
    (fn [data msg-hdr]
      (.processData consumer-cb data msg-hdr))
    pool-size))

(defn -createCarboniteLzfProducer [broker-url destination-description pool-size]
  (jms/create-carbonite-lzf-producer
    broker-url
    destination-description
    pool-size))

(defn -createCarboniteLzfConsumer [broker-url destination-description ^JmsConsumerCallback consumer-cb pool-size]
  (jms/create-carbonite-lzf-consumer
    broker-url
    destination-description
    (fn [data msg-hdr]
      (.processData consumer-cb data msg-hdr))
    pool-size))

(defn -startEmbeddedBroker [this]
  (let [broker-ref (:broker (.state this))]
    (if (nil? @broker-ref)
      (let [brkr (jms/start-broker (:broker-url (.state this)))]
        (dosync (ref-set broker-ref brkr))))))

(defn -stopEmbeddedBroker [this]
  (let [broker-ref (:broker (.state this))]
    (when (not (nil? @broker-ref))
      (jms/stop @broker-ref)
      (dosync (ref-set broker-ref nil)))))

