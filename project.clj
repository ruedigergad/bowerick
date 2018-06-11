;(defproject bowerick "2.2.5-SNAPSHOT"
(defproject bowerick "2.2.4"
  :description "Easing Simple JMS Tasks with Clojure (and Java)"
  :dependencies [[com.twitter/carbonite "1.5.0"]
                 [cheshire "5.8.0"]
                 [cli4clj "1.4.0"]
                 [clj-assorted-utils "1.18.2"]
                 [com.ning/compress-lzf "1.0.4"]
                 [com.taoensso/nippy "2.14.0"]
                 [javax.servlet/javax.servlet-api "4.0.1"]
                 [org.apache.activemq/activemq-broker "5.15.4" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-client "5.15.4" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-http "5.15.4" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-jaas "5.15.4" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-openwire-legacy "5.15.4" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-stomp "5.15.4" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.eclipse.jetty/jetty-server "9.4.11.v20180605"]
                 [org.eclipse.jetty.websocket/websocket-client "9.4.11.v20180605"]
                 [org.eclipse.jetty.websocket/websocket-server "9.4.11.v20180605"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.cli "0.3.7"]
                 [org.eclipse.paho/org.eclipse.paho.client.mqttv3 "1.2.0"]
                 [org.iq80.snappy/snappy "0.4"]
                 [org.springframework/spring-messaging "5.0.6.RELEASE"]
                 [org.springframework/spring-websocket "5.0.6.RELEASE"]
                 [org.slf4j/slf4j-simple "1.7.25"]
                 [rgad/stompjms-client "1.20-SNAPSHOT"]]
  :license {:name "Eclipse Public License (EPL) - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "This is the same license as used for Clojure."}
  :global-vars {*warn-on-reflection* true}
  :aot :all
  :prep-tasks [["compile" "bowerick.java-interfaces"]
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
               {:dependencies  [[jonase/eastwood "0.2.6" :exclusions  [org.clojure/clojure]]]}
             :test
               {:dependencies [[criterium "0.4.4"]]
                :test-paths ["test" "benchmark"]}}
  :plugins [[lein-cloverage "1.0.9"] [test2junit "1.3.3"] [lein-html5-docs "3.0.3"]]
  ;:jvm-opts ["-Djavax.net.debug=all"]
  )
