(defproject condense/ampere "0.4.1"
  :description "Ampere: UniDirectional Flow for VDOM."
  :url "https://github.com/condense/ampere"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.93" :scope "provided"]
                 [org.omcljs/om "0.9.0" :scope "provided"]
                 [reagent "0.5.1" :scope "provided"]
                 [carbon/rx "0.1.10"]]

  :profiles {:debug {:debug true}
             :dev   {:dependencies [[karma-reporter "1.0.1"]]
                     :plugins      [[lein-cljsbuild "1.1.3"]
                                    [lein-npm "0.6.2"]
                                    [lein-figwheel "0.5.4-4"]]}}

  :clean-targets [:target-path
                  "run/compiled/demo"]

  :resource-paths ["run/resources"]

  :jvm-opts       ["-Xmx1g" "-XX:+UseConcMarkSweepGC"]

  :plugins [[funcool/codeina "0.4.0" :exclusions [org.clojure/clojure]]]

  :source-paths ["src"]

  :test-paths ["test"]

  :cljsbuild {:builds {:dev  {:source-paths ["src"]}
                       :test {:source-paths ["src" "test"]
                              :compiler     {:output-to     "run/compiled/test.js"
                                             :source-map    "run/compiled/test.js.map"
                                             :output-dir    "run/compiled/test"
                                             :optimizations :simple
                                             :pretty-print  true}}}}

  ;; because of https://github.com/karma-runner/karma/issues/1746  we include our own fork of karma
  :npm {:dependencies [[karma "https://github.com/danielcompton/karma/archive/v0.13.19.tar.gz"]
                       [karma-cljs-test "0.1.0"]
                       [karma-chrome-launcher "0.2.0"]
                       [karma-junit-reporter "0.3.8"]]}

  :codeina {:sources  ["src"]
            :reader   :clojurescript
            :target   "docs/api"
            :defaults {:doc/format :markdown}}

  :aliases {"auto"        ["do" "clean," "cljsbuild" "auto" "test,"]
            "once"        ["do" "clean," "cljsbuild" "once" "test,"] })
