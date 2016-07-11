# bowerick

Easing Simple JMS Tasks with Clojure (and Java)

## Status

### Latest Available Version

[![Clojars Project](https://img.shields.io/clojars/v/bowerick.svg)](https://clojars.org/bowerick)

### Continuous Integration

[![Build Status](https://travis-ci.org/ruedigergad/bowerick.svg?branch=master)](https://travis-ci.org/ruedigergad/bowerick)

### Test Coverage

[![Coverage Status](https://coveralls.io/repos/github/ruedigergad/bowerick/badge.svg?branch=master)](https://coveralls.io/github/ruedigergad/bowerick?branch=master)

#### Detailed Test Results

Detailed test results are available as well:
[http://ruedigergad.github.io/bowerick/test-results/html/](http://ruedigergad.github.io/bowerick/test-results/html/)

## Documentation

### Examples

#### Minimal Working Example

    ; Can also be run in: lein repl
    (require '[bowerick.jms :as jms])
    (def url "tcp://127.0.0.1:61616")
    (def destination "/topic/my.test.topic")
    (def brkr (jms/start-broker url))
    (def consumer (jms/create-json-consumer url destination (fn [data] (println "Received:" data))))
    (def producer (jms/create-json-producer url destination))
    (producer "foo")
    ; nilReceived: foo
    (producer '(1 7 0 1))
    ; nilReceived: (1 7 0 1)
    (jms/close producer)
    (jms/close consumer)
    (jms/stop brkr)
    ; (quit)

#### Pooled Operation

    ; Can also be run in: lein repl
    (require '[bowerick.jms :as jms])
    (def url "tcp://127.0.0.1:61616")
    (def destination "/topic/my.test.topic")
    (def brkr (jms/start-broker url))
    (def pool-size 3)
    (def consumer (jms/create-json-consumer url destination (fn [data] (println "Received:" data)) pool-size))
    (def producer (jms/create-json-producer url destination pool-size))
    (producer "foo")
    (producer [1 7 0 1])
    (producer 42.0)
    ; nilReceived: foo
    ; Received: [1 7 0 1]
    ; Received: 42.0
    (jms/close producer)
    (jms/close consumer)
    (jms/stop brkr)
    ; (quit)

### API Docs

API docs are available:
http://ruedigergad.github.io/bowerick/doc/

### Cheat Sheet

#### Transport Connections

* Encrypted
    * OpenWire via TCP (with optional client authentication)

      ssl://127.0.0.1:42425
      
      ssl://127.0.0.1:42425?needClientAuth=true
    * STOMP via TCP (with optional client authentication)
      
      stomp+ssl://127.0.0.1:42423
      
      stomp+ssl://127.0.0.1:42423?needClientAuth=true
* Unencrypted
    * OpenWire via TCP
      
      tcp://127.0.0.1:42424
    * OpenWire via UDP
      
      udp://127.0.0.1:42426
    * STOMP via TCP
      
      stomp://127.0.0.1:42422

#### Pooling, Serialization, and Compression

Placeholders Used in the Cheat Sheet Examples

    (def server-url "tcp://127.0.0.1:42424")
    (def endpoint-description "/topic/my.topic.name")
    (def pool-size 10)
    (defn callback-fn [data] (println data))

* Single Message Operation
  
  (create-producer server-url endpoint-description)
  
  (create-consumer server-url endpoint-description callback-fn)
* Pooled Operation
   * Default Serialization
     
     (create-pooled-producer server-url endpoint-description pool-size)
     
     (create-pooled-consumer server-url endpoint-description callback-fn)
   * Carbonite (Kryo) Serialization
     
     (create-carbonite-producer server-url endpoint-description pool-size)
     
     (create-carbonite-consumer server-url endpoint-description callback-fn pool-size)
   * Carbonite (Kryo) Serialization with LZF Compression
     
     (create-carbonite-lzf-producer server-url endpoint-description pool-size)
     
     (create-carbonite-lzf-consumer server-url endpoint-description callback-fn pool-size)
   * Nippy Serialization
     
     (create-pooled-nippy-producer server-url endpoint-description pool-size)
     
     (create-pooled-nippy-consumer server-url endpoint-description callback-fn)
   * Nippy Serialization with Compression (LZ4, Snappy, LZMA2)
     
     (create-pooled-nippy-producer server-url endpoint-description pool-size {:compressor taoensso.nippy/lz4-compressor})
     
     (create-pooled-nippy-producer server-url endpoint-description pool-size {:compressor taoensso.nippy/snappy-compressor})
     
     (create-pooled-nippy-producer server-url endpoint-description pool-size {:compressor taoensso.nippy/lzma2-compressor})
     
     (create-pooled-nippy-consumer server-url endpoint-description callback-fn)
   * Nippy Serialization with LZF Compression
     
     (create-pooled-nippy-lzf-producer server-url endpoint-description pool-size)
     
     (create-pooled-nippy-lzf-consumer server-url endpoint-description callback-fn)

## License

Copyright © 2016 Ruediger Gad

Copyright © 2014, 2015 Frankfurt University of Applied Sciences

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

