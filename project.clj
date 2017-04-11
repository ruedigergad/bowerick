(defproject bowerick "1.99.16-SNAPSHOT"
  :description "Easing Simple JMS Tasks with Clojure (and Java)"
  :dependencies [[com.twitter/carbonite "1.5.0"]
                 [cheshire "5.7.0"]
                 [cli4clj "1.2.7"]
                 [clj-assorted-utils "1.17.1"]
                 [com.ning/compress-lzf "1.0.4"]
                 [com.taoensso/nippy "2.13.0"]
                 [javax.servlet/javax.servlet-api "3.1.0"]
                 [org.apache.activemq/activemq-broker "5.14.4" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-client "5.14.4" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-http "5.14.4" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-jaas "5.14.4" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-openwire-legacy "5.14.4" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.apache.activemq/activemq-stomp "5.14.4" :exclusions [org.eclipse.jetty.aggregate/jetty-all]]
                 [org.eclipse.jetty/jetty-server "9.4.3.v20170317"]
                 [org.eclipse.jetty.websocket/websocket-client "9.4.3.v20170317"]
                 [org.eclipse.jetty.websocket/websocket-server "9.4.3.v20170317"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [rgad/stompjms-client "1.20-SNAPSHOT"]
                 [org.eclipse.paho/org.eclipse.paho.client.mqttv3 "1.1.1"]
                 [org.springframework/spring-messaging "4.3.7.RELEASE"]
                 [org.springframework/spring-websocket "4.3.7.RELEASE"]
                 [org.slf4j/slf4j-simple "1.7.25"]]
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
  :plugins [[lein-cloverage "1.0.9"]]
  ;:jvm-opts ["-Djavax.net.debug=all"]
  )
