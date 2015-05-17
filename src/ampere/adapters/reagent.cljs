(ns ampere.adapters.reagent
  (:require [ampere.router :as router]
            [reagent.core  :refer [atom flush]]))

(defn subscribe [cell]
  (let [a (atom @cell)]
    (add-watch cell (gensym) #(reset! a %4))
    a))

(defn init! []
  (set! router/*flush-dom* flush))