(defproject condense/ampere "0.3.4"
  :description "Ampere: UniDirectional Flow for VDOM."
  :url "https://github.com/condense/ampere"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.34" :scope "provided"]
                 [org.omcljs/om "0.9.0" :scope "provided"]
                 [reagent "0.5.1" :scope "provided"]]

  :plugins [[funcool/codeina "0.3.0" :exclusions [org.clojure/clojure]]]

  :cljsbuild {:builds {:dev {:source-paths ["src"]}}}

  :codeina {:sources ["src"]
            :reader :clojurescript
            :target "docs/api"
            :defaults {:doc/format :markdown}})
