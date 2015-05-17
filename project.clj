(defproject ampere "0.1.0-SNAPSHOT"
  :description "Ampere: UniDirectional Flow for VDOM."
  :url "https://github.com/ul/ampere"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0-beta3"]
                 [org.clojure/clojurescript "0.0-3269" :scope "provided"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [tailrecursion/javelin "3.8.0"]
                 [vfsm "0.1.0-SNAPSHOT"]
                 ;; FIXME move adapters to separate packages
                 ;; Om
                 [sablono "0.3.4"]
                 [org.omcljs/om "0.8.8"]
                 ;; Hoplon
                 [tailrecursion/hoplon "6.0.0-alpha1"]
                 ;; Reagent
                 [reagent "0.5.0"]])
