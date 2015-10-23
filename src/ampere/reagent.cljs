(ns ampere.reagent
  (:require [ampere.router :as router]
            [ampere.core :as ampere]
            [reagent.core :as r]
            [reagent.ratom :refer [make-reaction]]))

(defn subscribe [v]
  (let [sub (ampere/subscribe v)
        a (r/atom @sub)
        id (gensym)]
    (add-watch sub id #(reset! a %4))
    (make-reaction #(deref a) :on-dispose #(remove-watch sub id))))

(defn init! []
  (set! router/*flush-dom* r/flush))
