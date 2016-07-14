(defproject bowerick "1.99.5-SNAPSHOT"
  :description "Easing Simple JMS Tasks with Clojure (and Java)"
  :dependencies [[com.twitter/carbonite "1.5.0"]
                 [cheshire "5.6.1"]
                 [cli4clj "1.2.0"]
                 [clj-assorted-utils "1.12.0"]
                 [com.ning/compress-lzf "1.0.3"]
                 [com.taoensso/nippy "2.11.1"]
                 [javax.servlet/javax.servlet-api "3.0.1"]
                 [org.apache.activemq/activemq-broker "5.13.3"]
                 [org.apache.activemq/activemq-client "5.13.3"]
                 [org.apache.activemq/activemq-http "5.13.3"]
                 [org.apache.activemq/activemq-jaas "5.13.3"]
                 [org.apache.activemq/activemq-openwire-legacy "5.13.3"]
                 [org.apache.activemq/activemq-stomp "5.13.3"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.2.4"]
                 [org.eclipse.jetty.websocket/javax-websocket-client-impl "9.2.17.v20160517"]
                 [rgad/stompjms-client "1.20-SNAPSHOT"]
                 [org.eclipse.paho/org.eclipse.paho.client.mqttv3 "1.1.0"]
                 [org.springframework/spring-messaging "4.3.0.RELEASE"]
                 [org.springframework/spring-websocket "4.3.0.RELEASE"]
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
  :test2junit-output-dir "ghpages/test-results"
  :test2junit-run-ant true
  :html5-docs-docs-dir "ghpages/doc"
  :html5-docs-ns-includes #"^bowerick.*"
  :html5-docs-repository-url "https://github.com/ruedigergad/bowerick/blob/master"
  :profiles {:repl
               {:dependencies  [[jonase/eastwood "0.2.3" :exclusions  [org.clojure/clojure]]]}
             :test
               {:dependencies [[criterium "0.4.4"]]
                :test-paths ["test" "benchmark"]}}
  :plugins [[lein-cloverage "1.0.6"]]
  ;:jvm-opts ["-Djavax.net.debug=all"]
  )
