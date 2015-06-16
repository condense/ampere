(defproject todomvc-ampere "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.7.0-RC1"]
                 [org.clojure/clojurescript "0.0-3308"]
                 [ampere "0.2.0-SNAPSHOT"]

                 ;[org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 ;[freactive.core "0.2.0-SNAPSHOT"]
                 [org.omcljs/om "0.8.8"]
                 [reagent "0.5.0"]

                 [sablono "0.3.4"]
                 [secretary "1.2.3"]]

  :plugins [[lein-cljsbuild "1.0.6"]]

  :hooks [leiningen.cljsbuild]

  :profiles {:dev  {:cljsbuild
                    {:builds {:client {:compiler
                                       {:optimizations        :none
                                        :source-map           true
                                        :source-map-timestamp true}}}}}

             :prod {:cljsbuild
                    {:builds {:client {:compiler
                                       {:optimizations :advanced
                                        :elide-asserts true
                                        :pretty-print  false}}}}}}

  :cljsbuild {:builds {:client {:source-paths ["src"]
                                :compiler
                                {:main todomvc.core
                                 :output-dir "target/client"
                                 :output-to "target/client.js"}}}})
