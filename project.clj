(defproject condense/ampere "0.3.2-SNAPSHOT"
  :description "Ampere: UniDirectional Flow for VDOM."
  :url "https://github.com/condense/ampere"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.145" :scope "provided"]
                 [org.clojure/core.async "0.2.371"]
                 [org.omcljs/om "0.9.0" :scope "provided"]
                 [reagent "0.5.1" :scope "provided"]]

  :plugins [[funcool/codeina "0.3.0" :exclusions [org.clojure/clojure]]]

  :codeina {:sources ["src"]
            :reader :clojurescript
            :target "docs/api"
            :defaults {:doc/format :markdown}})
