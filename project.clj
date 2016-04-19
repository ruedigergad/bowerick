(defproject bowerick "1.99.3"
  :description "Toolkit for using JMS with Clojure."
  :dependencies [[com.twitter/carbonite "1.5.0"]
                 [cheshire "5.5.0"]
                 [clj-assorted-utils "1.12.0"]
                 [com.ning/compress-lzf "1.0.3"]
                 [com.taoensso/nippy "2.11.1"]
                 [org.apache.activemq/activemq-broker "5.13.2"]
                 [org.apache.activemq/activemq-client "5.13.2"]
                 [org.apache.activemq/activemq-jaas "5.13.2"]
                 [org.apache.activemq/activemq-openwire-legacy "5.13.2"]
                 [org.apache.activemq/activemq-stomp "5.13.2"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.2.4"]
                 [org.fusesource.stompjms/stompjms-client "1.19"]
                 [org.slf4j/slf4j-simple "1.7.10"]]
  :license {:name "Eclipse Public License (EPL) - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "This is the same license as used for Clojure."}
  :global-vars {*warn-on-reflection* true}
  :aot :all
  :java-source-paths ["src-java"]
  :prep-tasks [["compile" "bowerick.java-interfaces"]
               ["javac" "src-java/bowerick/PooledBytesMessageProducer.java"]
               ["compile" "bowerick.JmsController"]
               "javac"
               "compile"]
  :main bowerick.main
  :test-selectors {:default (complement :benchmark)
                   :benchmark :benchmark}
  :profiles {:repl
               {:dependencies  [[jonase/eastwood "0.2.3" :exclusions  [org.clojure/clojure]]]}
             :test
               {:dependencies [[criterium "0.4.4"]]
                :test-paths ["test" "benchmark"]}}
  :plugins [[lein-cloverage "1.0.6"]])
