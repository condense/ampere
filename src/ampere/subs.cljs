(ns ampere.subs
  (:require-macros [freactive.macros :refer [rx]])
  (:require [freactive.core :as rx]
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

(defn when-contains [m k f & args]
  (if (contains? m k)
    (apply f m k args)
    m))

(defn invalidate
  ([] (invalidate app-db))
  ([db] (swap! cache dissoc db))
  ([db v] (swap! cache when-contains db update dissoc v)))

(defn register
  "Registers a handler function for an id."
  [key-v handler-fn]
  (if (contains? @key->fn key-v)
    (warn "ampere: overwriting subscription-handler for: " key-v))   ;; allow it, but warn.
  (invalidate app-db key-v)
  (swap! key->fn assoc key-v handler-fn))

(defn path-handler [db v]
  (rx (get-in @db v)))

(defn inject-teardown [rx f]
  (let [g (.-teardown rx)]
    (set! (.-teardown rx)
          (fn [rx]
            (when g (g rx))
            (f rx)))
    rx))

(defn make-sub [handler-fn v]
  (let [sub (handler-fn app-db v)]
    (alter-meta! sub assoc ::subscription v)
    sub))

(defn subscribe
  "Returns a reaction which observes a part of app-db."
  ([v]
   (let [key-v      (first-in-vector v)
         handler-fn (get @key->fn key-v path-handler)
         cache-key  [app-db v]]
     (if *cache?*
       (if-let [sub (get-in @cache cache-key)]
         sub
         (let [sub (inject-teardown (make-sub handler-fn v)
                                    #(swap! cache
                                            when-contains (cache-key 0)
                                            update dissoc (cache-key 1)))]
           (swap! cache assoc-in cache-key sub)
           sub))
       (make-sub handler-fn v))))
  ([db v] (binding [app-db db] (subscribe v))))

(defn sample [db v]
  "Sample subscription against immutable db value."
  (binding [*cache?* false]
    @(subscribe (rx/atom db) v)))

(defn trace []
  (keep (comp ::subscription meta) rx/*provenance*))