# bowerick

Easing Simple Message-oriented Middleware Tasks with Clojure (and Java)

[![Clojars Project](https://img.shields.io/clojars/v/bowerick.svg)](https://clojars.org/bowerick)
[![Build Status TravicCI](https://travis-ci.com/ruedigergad/bowerick.svg?branch=master)](https://travis-ci.com/ruedigergad/bowerick)
[![Build Status SemaphoreCI](https://semaphoreci.com/api/v1/ruedigergad/bowerick/branches/master/badge.svg)](https://semaphoreci.com/ruedigergad/bowerick)
[![lein test](https://github.com/ruedigergad/bowerick/actions/workflows/lein_test.yml/badge.svg)](https://github.com/ruedigergad/bowerick/actions/workflows/lein_test.yml)
[![Coverage Status](https://coveralls.io/repos/github/ruedigergad/bowerick/badge.svg?branch=master)](https://coveralls.io/github/ruedigergad/bowerick?branch=master)

bowerick somewhat started as "an accident with a few rubber bands, a liquid lunch, and a ~~particle accelerator~~ Message-oriented Middelware (MoM)".
It is based on ActiveMQ and various other related libraries etc.

Bowerick provides

- Command Line Tools
  - Broker/Server Mode
  - Client Mode
- A Library/Framework/API for
  - Clojure
  - Java

in a single Jar file.
For the single Jar file, download one of the "*-standalone.jar" files, from the "dist/" directory.
Alternatively, bowerick is also available from clojars.org to be included as dependency in other projects.

The original context, in which the foundation for bowerick was layed was experiments with different MoM protocols in different deployments.
As such, one goal of bowerick was kinda to address my own lazyness in the sense that I wanted to be able to use a multi-protocol MoM broker and library without requiring much configuration or complex deployments.
Later on, additinal functionality was added, such as a command line client application, which I initially used for debugging.

Bowerick supports multiple protocols as broker and as client and automatically bridges between protocols thanks to ActiveMQ.
The supported protocls are:

- OpenWire
- MQTT
- STOMP
- STOMP via WebSockets

## Links

### Detailed Test Results

Detailed test results are available as well:
[https://ruedigergad.github.io/bowerick/test-results/html/](http://ruedigergad.github.io/bowerick/test-results/html/)

### Blog Posts

On my website, I wrote some blog posts about bowerick:

[https://ruedigergad.com/category/libs/bowerick/](https://ruedigergad.com/category/libs/bowerick/)

In these blog posts, I make announcements about bowerick and discuss selected aspects of bowerick in more detail.

### API Docs

API docs are available:
[https://ruedigergad.github.io/bowerick/doc/](http://ruedigergad.github.io/bowerick/doc/)

## Documentation

The documentation tries to follow the scheme of the functionality as introduced above:

- Command Line Tools
  - Broker/Server Mode
  - Client Mode
- A Library/Framework/API
  - For Clojure
  - For Java

The combined functionality offered by bowerick is provided in the "*-standalone.jar" files, which can be downloaded from the "dist/" directory.
Alternatively, bowerick can be included as library in your applications via clojars.org.

### Command Line Examples

The command line examples all refer to bowerick distributed as "*-standalone.jar" file.

All available command line arguments etc. can be displayed via the help functionality as follows:

```
java -jar bowerick-<VERSION>-standalone.jar --help
```

Below, examples for some arguments are given.

#### Broker/Server Mode

##### Broker Startup and Transports Configuration

The initial main aim of the broker/server mode was to easily start a Message-oriented Middleware (MoM) broker/server.

The most basic way for starting a broker is as follows:

```
java -jar bowerick-<VERSION>-standalone.jar
```

This will start bowerick with an OpenWire transport listening on "tcp://127.0.0.1:61616".

Transports with alternative host names, IP addresses, port numbers, or protcols can be configured as follows:

```
# For MQTT via WebSockets
java -jar bowerick-<VERSION>-standalone.jar -u "mqtt://127.0.0.1:1864"
# For STOMP
java -jar bowerick-<VERSION>-standalone.jar -u "stomp://127.0.0.1:1864"
# For STOMP via WebSockets
java -jar bowerick-<VERSION>-standalone.jar -u "ws://127.0.0.1:1864"

```

For an overview of URLs that are supported, see the cheat sheet below.

In addition to opening a single transport, bowerick can also be started with multiple transports, e.g., as follows:

```
# Using MQTT and STOMP via WebSockets
java -jar bowerick-<VERSION>-standalone.jar -u "[ws://127.0.0.1:1864 mqtt://127.0.0.1:1701]"

```

Note the notation using square brackets to indicate the list of transports.
More transports can be added by adding their URLs to this list.

##### Message Generators

In addition to starting a broker, bowerick can also start a message along with the borker to produce traffic.
It provides some built-in message generators and can also be extended with custom message generators.

The first use case for a message generator was an experiment with A-Frame.
The syntax for starting this message generator is:

```
java -jar bowerick-<VERSION>-standalone.jar -A

```

This creates a message generator that produces 3D coordinates of a rotating dot.

Other built-in message generators can be started via their name.
Below are snippets for starting some built-in message generators:

```
# The hello-world generator creates message containin the string "hello world".
java -jar bowerick-<VERSION>-standalone.jar -G hello-world -I 1000

# The yin-yang generator creates 3D coordinates of dots showing a rotating yin yang symbol.
java -jar bowerick-<VERSION>-standalone.jar -G yin-yang -I 1000

# The heart4family generator creates 3D coordinates for a dot that moves in a heart shape path..
java -jar bowerick-<VERSION>-standalone.jar -G heart4family -I 1000
```

Note that these examples use an additional "-I 1000" argument.
This argument is used to configure the rough interval with which messages are sent in milliseconds.
A value of 1000 means that a message is generated roughly every second.

Other message generators require additional arguments.
Additional arguments to the message generator are passed via the "-X" command line argument.
Below some example are given.

```
# The txt-file-line producer sends the content of a text file line by line.
# I.e., each line will be sent in a separate message.
# The path to the text file to be sent is passed via -X ....
java -jar bowerick-<VERSION>-standalone.jar -G txt-file-line -I 1000 -X test/data/csv_input_test_file.txt

# The txt-file is a more generic version of txt-file-line.
# In addition to the path of the text file to be sent it also takes a Clojure regular expression.
# The regular expression is used for splitting the text file and the resulting splitted parts are the units sent with each message.
java -jar bowerick-<VERSION>-standalone.jar -G txt-file -I 1000 -X '["test/data/csv_input_test_file.txt" #"[\\n,]"]'
# Note how the arguments are noted within square brackets (a Clojure vector), when more than one argument shall be passed to the message generator.

# The pcap-file message generator sends the raw packet data from a pcap packet capture file.
# It sends one packet per message.
java -jar bowerick-<VERSION>-standalone.jar -G pcap-file -I 1000 -X test/data/binary_pcap_data_input_test.pcap
```

The examples are run from the base directory of the bowerick git project.
This is important for relative file paths that are shown in the examples to work.


#### Client Mode

The client mode is started via:

```
java -jar bowerick-<VERSION>-standalone.jar -c
```

In client mode, bowerick displays an interactive command line interface (CLI).
Via the CLI, commands can be entered for using the bowerick client mode.

To get a list of all commands type "help".
The client mode CLI also supports tab completion and hints.
I suggest to press <TAB> once or twice in different places to see the different ways how tab completions and hints can be used.

### Clojure Library Examples

For using bowerick as Clojure library, the best way is to include it as dependency in your project.

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

#### Multiple Transports

    ; Can also be run in: lein repl
    (require '[bowerick.jms :as jms])
    (def urls ["tcp://127.0.0.1:61616" "stomp://127.0.0.1:61617" "ws://127.0.0.1:61618" "mqtt://127.0.0.1:61619"])
    (def destination "/topic/my.test.topic")
    (def brkr (jms/start-broker urls))
    (def consumers (doall (map-indexed (fn [idx url] (jms/create-json-consumer url destination (fn [data] (Thread/sleep (* 100 idx)) (println "Received" url data)))) urls)))
    (def producer (jms/create-json-producer (first urls) destination))
    (producer "foo")
    ; Received tcp://127.0.0.1:61616 foo
    ; Received stomp://127.0.0.1:61617 foo
    ; Received ws://127.0.0.1:61618 foo
    ; Received mqtt://127.0.0.1:61619 foo
    (jms/close producer)
    (doseq [consumer consumers] (jms/close consumer))
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

### Container Image

Below is an example on using the container image with Docker:

Start a container with the default settings in the entrypoint and forward all ports:

```
docker run -p 1031:1031 -p 1701:1701 -p 1864:1864 -p 2000:2000 -p 11031:11031 -p 11701:11701 -p 11864:11864 -p 12000:12000 ruedigergad/bowerick:latest
```

Connect a Java client to the container:

```
java -jar dist/bowerick-2.9.6-standalone.jar -B -u "tcp://127.0.0.1:1031"
```

Connect a client container by setting CUSTOM_ARGS:

```
docker run --net host -it -e CUSTOM_ARGS="-B -u tcp://127.0.0.1:1031" ruedigergad/bowerick:latest
```

Connect a client container by overriding the entrypoint:

```
docker run --net host -it --entrypoint "/bin/sh" ruedigergad/bowerick:latest "-c" "java -jar bowerick*standalone.jar -B -u tcp://127.0.0.1:1031"
```

Start broker without message generator:

```
docker run -e GEN=false -p 1031:1031 -p 1701:1701 -p 1864:1864 -p 2000:2000 -p 11031:11031 -p 11701:11701 -p 11864:11864 -p 12000:12000 ruedigergad/bowerick:2.9.6
```

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
    * STOMP via WebSockets (with optional client authentication)
      
      wss://127.0.0.1:42427
      
      wss://127.0.0.1:42427?needClientAuth=true
    * MQTT via TCP (with optional client authentication)

      mqtt+ssl://127.0.0.1:42429

      mqtt+ssl://127.0.0.1:42429?needClientAuth=true
* Unencrypted
    * OpenWire via TCP
      
      tcp://127.0.0.1:42424
    * OpenWire via UDP
      
      udp://127.0.0.1:42426
    * STOMP via TCP
      
      stomp://127.0.0.1:42422

    * STOMP via WebSocket

      ws://127.0.0.1:42428
    * MQTT via TCP

      mqtt://127.0.0.1:42430

#### Serialization, Compression, and Pooling

Placeholders Used in the Cheat Sheet Examples

    (def server-url "tcp://127.0.0.1:42424")
    (def destination-description "/topic/my.topic.name")
    (def pool-size 10)
    (defn callback-fn [data] (println data))

* Default Serialization
 
 (create-producer server-url destination-description pool-size)
 
 (create-consumer server-url destination-description callback-fn pool-size)
* JSON Serialization (Using Cheshire)

 (create-json-producer server-url destination-description pool-size)
 
 (create-json-consumer server-url destination-description callback-fn pool-size)
* Carbonite (Kryo) Serialization
 
 (create-carbonite-producer server-url destination-description pool-size)
 
 (create-carbonite-consumer server-url destination-description callback-fn pool-size)
* Carbonite (Kryo) Serialization with LZF Compression
 
 (create-carbonite-lzf-producer server-url destination-description pool-size)
 
 (create-carbonite-lzf-consumer server-url destination-description callback-fn pool-size)
* Nippy Serialization
 
 (create-nippy-producer server-url destination-description pool-size)
 
 (create-nippy-consumer server-url destination-description callback-fn pool-size)
* Nippy Serialization with Compression (LZ4, Snappy, LZMA2)
 
 (create-nippy-producer server-url destination-description pool-size {:compressor taoensso.nippy/lz4-compressor})
 
 (create-nippy-producer server-url destination-description pool-size {:compressor taoensso.nippy/snappy-compressor})
 
 (create-nippy-producer server-url destination-description pool-size {:compressor taoensso.nippy/lzma2-compressor})
* Nippy Serialization with LZF Compression
 
 (create-nippy-lzf-producer server-url destination-description pool-size)
 
 (create-nippy-lzf-consumer server-url destination-description callback-fn pool-size)

## License

Copyright © 2016 - 2021 Ruediger Gad

Copyright © 2014 - 2015 Frankfurt University of Applied Sciences

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

