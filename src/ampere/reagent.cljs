(ns ampere.reagent
  (:require [ampere.router :as router]
            [ampere.core :as ampere]
            [freactive.core :refer [dispose]]
            [reagent.core :as r]
            [reagent.ratom :refer [make-reaction]]))

(defn subscribe [v]
  (let [sub (ampere/subscribe v)
        a (r/atom @sub)]
    (.addInvalidationWatch sub :reagent #(reset! a (.rawDeref sub)))
    (make-reaction #(deref a)
                   :on-dispose #(do
                                  (.removeInvalidationWatch sub :reagent)
                                  (dispose sub)))))

(defn init! []
  (set! router/*flush-dom* r/flush))
