;(defproject bowerick "2.9.7-SNAPSHOT"
(defproject bowerick "2.9.6"
  :description "Easing Simple JMS Tasks with Clojure (and Java)"
  :dependencies [[com.twitter/carbonite "1.5.0"]
                 [cheshire "5.11.0"]
                 [cli4clj "1.9.0"]
                 [clj-assorted-utils "1.19.0"]
                 [com.ning/compress-lzf "1.1.2"]
                 [com.taoensso/nippy "3.2.0"]
                 [javax.servlet/javax.servlet-api "4.0.1"]
                 [org.apache.activemq/activemq-broker "5.17.3" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-client "5.17.3" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-http "5.17.3" :exclusions [org.eclipse.jetty.aggregate/jetty-all commons-logging/commons-logging]]
                 [org.apache.activemq/activemq-jaas "5.17.3" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-openwire-legacy "5.17.3" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-stomp "5.17.3" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.eclipse.jetty/jetty-server "9.4.50.v20221201"]
                 [org.eclipse.jetty.websocket/websocket-client "9.4.50.v20221201"]
                 [org.eclipse.jetty.websocket/websocket-server "9.4.50.v20221201"]
                 [org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.cli "1.0.214"]
                 [org.eclipse.paho/org.eclipse.paho.client.mqttv3 "1.2.5"]
                 [org.iq80.snappy/snappy "0.4"]
                 [org.springframework/spring-messaging "5.3.25"]
                 [org.springframework/spring-websocket "5.3.25"]
                 [org.slf4j/slf4j-simple "2.0.6"]
                 [rgad/stompjms-client "1.20-SNAPSHOT"]
                 [juxt/dirwatch "0.2.5"]
                 [org.tcrawley/dynapath "1.1.0"]
                 [spootnik/signal "0.2.4"]]
  :license {:name "Eclipse Public License (EPL) - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "This is the same license as used for Clojure."}
  :global-vars {*warn-on-reflection* true}
  :java-source-paths ["test/data"]
  :prep-tasks [["compile" "bowerick.java-interfaces"]
               ["compile" "bowerick.JmsController"]
               "javac"
               "compile"]
  :main bowerick.main
  :aot :all
  :test-selectors {:default (complement :benchmark)
                   :benchmark :benchmark}
  :test2junit-output-dir "docs/test-results"
  :test2junit-run-ant true
  :html5-docs-docs-dir "docs/doc"
  :html5-docs-ns-includes #"^bowerick.*"
  :html5-docs-repository-url "https://github.com/ruedigergad/bowerick/blob/master"
  :profiles {:repl
               {:dependencies  [[jonase/eastwood "1.3.0" :exclusions  [org.clojure/clojure]]]}
             :test
               {:dependencies [[criterium "0.4.6"]]
                :test-paths ["test" "benchmark"]}}
  :plugins [[lein-cloverage "1.2.2"] [test2junit "1.4.2"] [lein-html5-docs "3.0.3"]]
  ; Explicitly forcing TLSv1.2, for now, because of: https://bugs.openjdk.java.net/browse/JDK-8211426
  ;:jvm-opts ["-Djavax.net.debug=all" "-Djdk.tls.server.protocols=TLSv1.2" "-Djdk.tls.client.protocols=TLSv1.2"]
  ;:jvm-opts ["-Djavax.net.debug=all"]
  )
