(defproject ampere "0.2.0-SNAPSHOT"
  :description "Ampere: UniDirectional Flow for VDOM."
  :url "https://github.com/ul/ampere"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0-RC1"]
                 [org.clojure/clojurescript "0.0-3308" :scope "provided"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [ampere/freactive.core "0.2.0"]
                 [org.omcljs/om "0.8.8" :scope "provided"]
                 [reagent "0.5.0" :scope "provided"]])
