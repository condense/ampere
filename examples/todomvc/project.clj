(defproject todomvc-ampere "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.7.0-beta3"]
                 [org.clojure/clojurescript "0.0-3269"]
                 [ampere "0.1.0-SNAPSHOT"]
                 [secretary "1.2.3"]]

  :plugins [[lein-cljsbuild "1.0.5"]]

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
