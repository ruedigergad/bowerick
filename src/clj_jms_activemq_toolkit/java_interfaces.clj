;;;
;;;   Copyright 2014, University of Applied Sciences Frankfurt am Main
;;;
;;;   This software is released under the terms of the Eclipse Public License 
;;;   (EPL) 1.0. You can find a copy of the EPL at: 
;;;   http://opensource.org/licenses/eclipse-1.0.php
;;;

(ns
  ^{:author "Ruediger Gad",
    :doc "Interfaces for Java interop."} 
  clj-jms-activemq-toolkit.java-interfaces)

(gen-interface
  :name clj_jms_activemq_toolkit.JmsConsumer
  :methods [[processObject [Object] void]])

(gen-interface
  :name clj_jms_activemq_toolkit.JmsProducer
  :methods [[sendObject [Object] void]])

(gen-interface
  :name clj_jms_activemq_toolkit.JmsController
  :methods [[connectConsumer [String clj_jms_activemq_toolkit.JmsConsumer] void]
            [createProducer [String] clj_jms_activemq_toolkit.JmsProducer]])

