(defproject bowerick "1.99.3"
  :description "Toolkit for using JMS with Clojure."
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.cli "0.2.4"]
                 [com.ning/compress-lzf "1.0.3"]
                 [org.xerial.snappy/snappy-java "1.1.2"]
                 [com.esotericsoftware/kryo "3.0.3"]
                 [org.apache.activemq/activemq-broker "5.12.1"]
                 [org.apache.activemq/activemq-client "5.12.1"]
                 [org.apache.activemq/activemq-jaas "5.12.1"]
                 [org.apache.activemq/activemq-openwire-legacy "5.12.1"]
                 [org.apache.activemq/activemq-stomp "5.12.1"]
                 [org.fusesource.stompjms/stompjms-client "1.19"]
                 [org.slf4j/slf4j-simple "1.7.10"]
                 [clj-assorted-utils "1.9.1"]
                 [cheshire "5.5.0"]]
  :jvm-opts ["-Djavax.net.ssl.keyStore=test/ssl/broker.ks" "-Djavax.net.ssl.keyStorePassword=password" "-Djavax.net.ssl.trustStore=test/ssl/broker.ts" "-Djavax.net.ssl.trustStorePassword=password"]
  :license {:name "Eclipse Public License (EPL) - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "This is the same license as used for Clojure."}
  :global-vars {*warn-on-reflection* true}
  :aot :all
  :java-source-paths ["src-java"]
  :prep-tasks [["compile" "bowerick.java-interfaces"]
               ["javac" "src-java/bowerick/PooledBytesMessageProducer.java"]
               ["compile" "bowerick.JmsController"] "javac" "compile"]
  :main bowerick.main
  :plugins [[lein-cloverage "1.0.2"]])
