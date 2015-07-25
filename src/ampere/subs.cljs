(ns ampere.subs
  (:require-macros [freactive.macros :refer [rx]])
  (:require freactive.core
            [ampere.db :refer [app-db]]
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

(defn path-handler [db v]
  (rx (get-in @db v)))

(defn subscribe
  "Returns a reaction which observes a part of app-db.
  FIXME allow static subscriptions, wrap them to be not disposed by adapter."
  ([v] (subscribe app-db v))
  ([db v]
   (let [key-v (first-in-vector v)
         handler-fn (get @key->fn key-v path-handler)]
     (handler-fn db v))))

(defn sample [db v]
  "Sample subscription against immutable db value."
  @(subscribe (freactive.core/atom db) v))