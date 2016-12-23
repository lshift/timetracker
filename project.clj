(defproject time-tracker "1.0.0"
  :description "Timetracker"
  :url "https://github.com/lshift/timetracker-web"
  :license {:name "Proprietary"}

  :min-lein-version "2.0.0"
  ;; Note to developer. Please keep these sorted in alphabetical order.
  :dependencies [[ch.qos.logback/logback-classic "1.1.6"]
                 [clj-bonecp-url "0.1.1"]
                 [clj-http "2.2.0"]
                 [clj-time "0.11.0"]
                 [cljs-ajax "0.5.8"]
                 [cljsjs/react-with-addons "15.2.1-0"]
                 [com.andrewmcveigh/cljs-time "0.5.0-alpha1"]
                 [com.fasterxml.jackson.core/jackson-core "2.7.3"]
                 [com.google.guava/guava "19.0"]
                 [com.jolbox/bonecp "0.8.0.RELEASE"]
                 [com.stuartsierra/component "0.3.1"]
                 [commons-codec "1.10"]
                 [compojure "1.5.1"]
                 [enlive "1.1.6"]
                 [environ "1.0.3"]
                 [kioo "0.5.0-SNAPSHOT" :exclusions [cljsjs/react]]
                 [liberator "0.14.0"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.293"]
                 [org.clojure/core.async "0.2.374"]
                 [org.clojure/core.match "0.2.2"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [org.clojure/test.check "0.9.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.flywaydb/flyway-core "4.0"]
                 [postgresql/postgresql "9.3-1102.jdbc41"]
                 [prismatic/schema "1.0.5"]
                 [reagent "0.6.0" :exclusions [cljsjs/react]]
                 [ring "1.5.0"]
                 [ring/ring-jetty-adapter "1.5.0"]
                 [ring/ring-json "0.4.0"]
                 [com.cemerick/url "0.1.1"]
                 [sablono "0.7.0" :exclusions [cljsjs/react]]
                 [secretary "1.2.3"]
                 [yesql "0.5.3"]]

  ; Note that these only seem to apply to application dependencies, not plugins
  ; If you have a common dependency, lock it down in :dependencies not here
  :managed-dependencies [[com.cemerick/clojurescript.test "0.1.0"]
                         [com.google.code.gson/gson "2.3.1"]
                         [commons-io "2.5"]
                         [hiccup "1.0.5"]
                         [org.eclipse.jetty/jetty-io "9.2.12.v20150709"]
                         [org.eclipse.jetty/jetty-util "9.2.12.v20150709"]]

  :plugins [[lein-cljsbuild "1.1.3"]
            [lein-shell "0.5.0"]
            [lein-environ "1.0.3"]
            [lein-cljfmt "0.5.3" :exclusions [org.clojure/clojure]]]

  :profiles {:dev {:plugins [[com.cemerick/austin "0.1.6" :exclusions [org.clojure/clojurescript org.clojure/clojure]]
                             [lein-doo "0.1.6" :exclusions [org.clojure/clojure]]
                             [lein-junit "1.1.8" :exclusions [org.clojure/clojure]]
                             [lein-figwheel "0.5.8" :exclusions [org.clojure/clojure]]
                             [lein-auto "0.1.2"]]
                   :dependencies [[org.clojure/tools.namespace "0.2.10"]
                                  [ring/ring-mock "0.3.0"]
                                  [com.cemerick/pomegranate "0.3.0"]
                                  [org.codehaus.plexus/plexus-utils "3.0"]
                                  [com.hypirion/io "0.3.1"]
                                  [org.hamcrest/hamcrest-library "1.3"]
                                  [junit/junit "4.12"]
                                  [org.apache.commons/commons-pool2 "2.2"]
                                  [org.apache.commons/commons-lang3 "3.4"]
                                  [org.seleniumhq.selenium/selenium-java "2.52.0"]
                                  [org.seleniumhq.selenium/selenium-htmlunit-driver "2.52.0"]
                                  [org.seleniumhq.selenium/selenium-server "2.52.0"]
                                  [com.fasterxml.jackson.core/jackson-databind "2.4.1.3"]
                                  [hickory "0.6.0"]
                                  [clj-webdriver "0.7.2"]]
                   :source-paths ["dev"]
                   :junit ["test/java"]
                   :junit-formatter "xml"
                   :junit-results-dir "test-results"
                   :java-source-paths ["test/java"]
                   :junit-test-file-pattern #".*IT.java$"
                   :aot ^:replace []}
             :local-dev {:env {:database-url "postgres://timetracker:ttpass6@localhost/timetracker-test"}}
             :teamcity {:plugins [[lein-teamcity "0.2.2"]]
                        :monkeypatch-clojure-test false}}

  :source-paths ["src/clj" "test/clj"]
  :resource-paths ["resources"]

  :aot [time-tracker.main, clojure.tools.logging.impl]
  :main time-tracker.main
  :repl-options {:init-ns user}

  :javac-options ["-target" "1.8" "-source" "1.8"]
  :jvm-opts ^:replace ["-XX:-TieredCompilation"]

  :doo {:build "test"
        :debug true}

  :figwheel {:repl false}

  :cljsbuild {:builds {:dev {:source-paths ["src/clj"]
                             :figwheel {:on-jsload "time-tracker.bootstrap/fig-reload"}
                             :compiler {:main time-tracker.bootstrap
                                        :output-to "target/classes/time_tracker/public/out/timetracker.js"
                                        :output-dir "target/classes/time_tracker/public/out"
                                        :asset-path "out"
                                        :optimizations :none
                                        :source-map true}}
                       :prod {:source-paths ["src/clj"]
                              :compiler {:main time-tracker.bootstrap
                                         :output-to "target/classes/time_tracker/public/out/timetracker.js"
                                         :output-dir "target/classes/time_tracker/public/out/prod-js"
                                         :optimizations :advanced
                                         :source-map "target/classes/time_tracker/public/out/timetracker.js.map"}}
                       :test {:source-paths ["src/clj" "test/clj"]
                              :compiler {:main time-tracker.core-test
                                         :target :nodejs
                                         :output-to "target/cljs/run-tests.js"
                                         :output-dir "target/cljs"
                                         :optimizations :none
                                         :pretty-print true}}}})
