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
  bowerick.java-interfaces)

(gen-interface
  :name bowerick.Closable
  :methods [[close [] void]])

(gen-interface
  :name bowerick.JmsConsumerCallback
  :methods [[processObject [Object] void]])

(gen-interface
  :name bowerick.JmsProducer
  :extends [bowerick.Closable]
  :methods [[sendObject [Object] void]])

