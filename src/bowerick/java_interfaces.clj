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
    :doc "Interfaces for Java interop."} 
  bowerick.java-interfaces)

#_{:clj-kondo/ignore [:unresolved-symbol]}
(gen-interface
  :name bowerick.JmsConsumerCallback
  :methods [[processData [Object Object] void]])

#_{:clj-kondo/ignore [:unresolved-symbol]}
(gen-interface
  :name bowerick.JmsProducer
  :extends [java.lang.AutoCloseable]
  :methods [[sendData [Object] void]
            [sendData [Object Object] void]])

#_{:clj-kondo/ignore [:unresolved-symbol]}
(gen-interface
  :name bowerick.MessageGenerator
  :methods [[generateMessage [bowerick.JmsProducer] void]])
