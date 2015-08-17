(ns ampere.reagent
  (:require [ampere.router :as router]
            [ampere.core :as ampere]
            [freactive.core :refer [dispose]]
            [reagent.core :as r]
            [reagent.ratom :refer [make-reaction]]))

(defn subscribe [v]
  (let [sub (ampere/subscribe v)
        a (r/atom @sub)
        id (gensym)]
    (.addInvalidationWatch sub id #(reset! a (.rawDeref sub)))
    (make-reaction #(deref a)
                   :on-dispose #(do
                                  (.removeInvalidationWatch sub id)
                                  (dispose sub)))))

(defn init! []
  (set! router/*flush-dom* r/flush))
