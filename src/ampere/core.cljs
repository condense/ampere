(ns ampere.core
  "Ampere API entry point. Re-export frequently used functions"
  (:require [ampere.handlers :as handlers]
            [ampere.subs :as subs]
            [ampere.router :as router]
            [ampere.utils :as utils]
            [ampere.middleware :as middleware]
            [ampere.db :refer [app-db]]
            [freactive.core :as rx]))

(def dispatch router/dispatch)
(def dispatch-sync router/dispatch-sync)

(def clear-event-handlers! handlers/clear-handlers!)
(def setter handlers/setter)

(def register-sub subs/register)
(def clear-sub-handlers! subs/clear-handlers!)
(def subscribe subs/subscribe)
(def sample subs/sample)

(def pure middleware/pure)
(def debug middleware/debug)
(def undoable middleware/undoable)
(def path middleware/path)
(def enrich middleware/enrich)
(def trim-v middleware/trim-v)
(def after middleware/after)

(def set-loggers!
  "Ampere uses the logging functions: warn, log, error, group and groupEnd
   By default, these functions map directly to the js/console implementations
   But you can override with your own (set or subset):
       (set-loggers!  {:warn my-warn
                       :log  my-looger ...})"
  utils/set-loggers!)

;; ## Convenience API

(defn register-handler
  "Almost 100% of handlers will be pure, so make it easy to
   register with `pure` middleware in the correct (left-hand-side) position."
  ([id handler]
   (handlers/register-base id pure handler))
  ([id middleware handler]
   (handlers/register-base id [pure middleware] handler)))

(defn init! [{:keys [handlers subs db logger-level] :as config}]
  (doseq [[id & middleware+handler] handlers]
    (let [v (flatten middleware+handler)]
      (register-handler id (-> v butlast vec) (last v))))
  (doseq [[id sub] subs]
    (register-sub id sub))
  (swap! app-db merge db)
  (when logger-level
    (utils/set-logger-level! logger-level)))

(defn bind-fn
  "Wrap your async handlers (of DOM, AJAX events) in `bind-fn` to preserve `app-db` and `*provenance*` context"
  [f]
  (let [db app-db
        router-prov router/*provenance*
        rx-prov rx/*provenance*]
    (fn [& args]
      (binding [app-db db
                router/*provenance* router-prov
                rx/*provenance* rx-prov]
        (apply f args)))))

(defn callback
  "Return a callback fn which will dispatch event-v with any callback args appended."
  [event-v]
  (bind-fn (fn [& args]
             (dispatch (into event-v args)))))