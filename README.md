# bowerick

Easing the Use of JMS with Clojure (and Java)

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

    lein repl
    bowerick.main=> (def url "tcp://127.0.0.1:61616")
    bowerick.main=> (def endpoint "/topic/my.test.topic")
    bowerick.main=> (def brkr (start-broker url))
    bowerick.main=> (def consumer (create-consumer url endpoint (fn [data] (println "Received:" data))))
    bowerick.main=> (def producer (create-producer url endpoint))
    bowerick.main=> (producer "foo")
    nilReceived: foo
    bowerick.main=> (producer [1 7 0 1])
    nilReceived: [1 7 0 1]
    bowerick.main=> (close producer)
    bowerick.main=> (close consumer)
    bowerick.main=> (.stop brkr)
    bowerick.main=> (quit)

#### Pooled Operation with Compression   

    lein repl
    bowerick.main=> (def url "tcp://127.0.0.1:61616")
    bowerick.main=> (def endpoint "/topic/my.test.topic")
    bowerick.main=> (def brkr (start-broker url))
    bowerick.main=> (def consumer (create-pooled-carbonite-lzf-consumer url endpoint (fn [data] (println "Received:" data))))
    bowerick.main=> (def pool-size 3)
    bowerick.main=> (def producer (create-pooled-carbonite-lzf-producer url endpoint pool-size))
    bowerick.main=> (producer "foo")
    bowerick.main=> (producer [1 7 0 1])
    bowerick.main=> (producer 42.0)
    nilReceived: foo

    Received: [1 7 0 1]
    Received: 42.0
    bowerick.main=> (close producer)
    bowerick.main=> (close consumer)
    bowerick.main=> (.stop brkr)
    bowerick.main=> (quit)

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
   * Criptonite (Kryo) Serialization
       (create-pooled-cryptonite-producer server-url endpoint-description pool-size)
       (create-pooled-cryptonite-consumer server-url endpoint-description callback-fn)
   * Criptonite (Kryo) Serialization with LZF Compression
       (create-pooled-cryptonite-lzf-producer server-url endpoint-description pool-size)
       (create-pooled-cryptonite-lzf-consumer server-url endpoint-description callback-fn)
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

