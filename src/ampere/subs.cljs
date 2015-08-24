(ns ampere.subs
  (:require-macros [freactive.macros :refer [rx]])
  (:require freactive.core
            [goog.object :as obj]
            [ampere.db :refer [app-db]]
            [ampere.utils :refer [first-in-vector warn error]]))

(def ^:private key->fn "handler-id → handler-fn" (atom {}))
(def ^:private cache "[db v] → rx" (atom {}))
(def ^:dynamic *cache?* true)

(defn clear-handlers!
  "Unregisters all subscription handlers."
  []
  (reset! cache {})
  (reset! key->fn {}))

(defn remove-keys [pred m]
  (if m (reduce-kv (fn [m k _] (if (pred k) (dissoc m k) m)) m m) {}))

(defn register
  "Registers a handler function for an id."
  [key-v handler-fn]
  (if (contains? @key->fn key-v)
    (warn "ampere: overwriting subscription-handler for: " key-v))   ;; allow it, but warn.
  (swap! cache (partial remove-keys (comp (partial = key-v) first second))) ; [db [key-v ...]]
  (swap! key->fn assoc key-v handler-fn))

(defn path-handler [db v]
  (rx (get-in @db v)))

(defn subscribe
  "Returns a reaction which observes a part of app-db."
  ([v]
   (let [key-v (first-in-vector v)
         handler-fn (get @key->fn key-v path-handler)
         cache-key [app-db v]]
     (if *cache?*
       (if-let [sub (get @cache cache-key)]
         sub
         (let [sub (handler-fn app-db v)
               sub (freactive.core/rx* #(deref sub) true #(swap! cache dissoc cache-key))]
           (obj/set sub "__ampere_v" v)
           (swap! cache assoc cache-key sub)
           sub))
       (let [sub (handler-fn app-db v)]
         (obj/set sub "__ampere_v" v)
         sub))))
  ([db v] (binding [app-db db] (subscribe v))))

(defn sample [db v]
  "Sample subscription against immutable db value."
  (binding [*cache?* false]
    @(subscribe (freactive.core/atom db) v)))