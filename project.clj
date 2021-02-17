(defproject bowerick "2.7.1-SNAPSHOT"
;(defproject bowerick "2.7.0"
  :description "Easing Simple JMS Tasks with Clojure (and Java)"
  :dependencies [[com.twitter/carbonite "1.5.0"]
                 [cheshire "5.10.0"]
                 [cli4clj "1.7.6"]
                 [clj-assorted-utils "1.18.5"]
                 [com.ning/compress-lzf "1.1"]
                 [com.taoensso/nippy "3.1.1"]
                 [javax.servlet/javax.servlet-api "4.0.1"]
                 [org.apache.activemq/activemq-broker "5.16.1" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-client "5.16.1" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-http "5.16.1" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-jaas "5.16.1" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-openwire-legacy "5.16.1" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-stomp "5.16.1" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.eclipse.jetty/jetty-server "9.4.36.v20210114"]
                 [org.eclipse.jetty.websocket/websocket-client "9.4.36.v20210114"]
                 [org.eclipse.jetty.websocket/websocket-server "9.4.36.v20210114"]
                 [org.clojure/clojure "1.10.2"]
                 [org.clojure/tools.cli "1.0.194"]
                 [org.eclipse.paho/org.eclipse.paho.client.mqttv3 "1.2.5"]
                 [org.iq80.snappy/snappy "0.4"]
                 [org.springframework/spring-messaging "5.3.4"]
                 [org.springframework/spring-websocket "5.3.4"]
                 [org.slf4j/slf4j-simple "1.7.30"]
                 [rgad/stompjms-client "1.20-SNAPSHOT"]]
  :license {:name "Eclipse Public License (EPL) - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "This is the same license as used for Clojure."}
  :global-vars {*warn-on-reflection* true}
  :prep-tasks [["compile" "bowerick.java-interfaces"]
               ["compile" "bowerick.JmsController"]
               "compile"]
  :main bowerick.main
  :aot :all
  :test-selectors {:default (complement :benchmark)
                   :benchmark :benchmark}
  :test2junit-output-dir "ghpages/test-results"
  :test2junit-run-ant true
  :html5-docs-docs-dir "ghpages/doc"
  :html5-docs-ns-includes #"^bowerick.*"
  :html5-docs-repository-url "https://github.com/ruedigergad/bowerick/blob/master"
  :profiles {:repl
               {:dependencies  [[jonase/eastwood "0.3.14" :exclusions  [org.clojure/clojure]]]}
             :test
               {:dependencies [[criterium "0.4.6"]]
                :test-paths ["test" "benchmark"]}}
  :plugins [[lein-cloverage "1.0.9"] [test2junit "1.3.3"] [lein-html5-docs "3.0.3"]]
  ; Explicitly forcing TLSv1.2, for now, because of: https://bugs.openjdk.java.net/browse/JDK-8211426
  ;:jvm-opts ["-Djavax.net.debug=all" "-Djdk.tls.server.protocols=TLSv1.2" "-Djdk.tls.client.protocols=TLSv1.2"]
  ;:jvm-opts ["-Djavax.net.debug=all"]
  )
