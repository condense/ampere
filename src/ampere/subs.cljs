(ns ampere.subs
  (:require [reagent.ratom :refer [make-reaction]]
            [ampere.db    :refer [app-db]]
            [ampere.utils :refer [first-in-vector warn error]]))

(def ^:private key->fn "handler-id → handler-fn" (atom {}))

(defn clear-handlers!
  "Unregisters all subscription handlers."
  []
  (reset! key->fn {}))

(defn register
  "Registers a handler function for an id."
  [key-v handler-fn]
  (if (contains? @key->fn key-v)
    (warn "ampere: overwriting subscription-handler for: " key-v))   ;; allow it, but warn.
  (swap! key->fn assoc key-v handler-fn))

(defn subscribe
  "Returns a cell (or type returned by *adapt*) which observes a part of app-db."
  [v]
  (let [key-v       (first-in-vector v)
        handler-fn  (get @key->fn key-v)]
    (if (nil? handler-fn)
      (do
        (warn "ampere: no subscription handler registered for: \"" key-v "\".  Subscribing to path.")
        (make-reaction #(get-in @app-db v)))
      (handler-fn app-db v))))