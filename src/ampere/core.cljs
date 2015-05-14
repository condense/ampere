(ns ampere.core
  "Ampere API entry point. Re-export frequently used functions"
  (:require [tailrecursion.javelin :refer-macros [cell=]]
            [ampere.handlers :as handlers]
            [ampere.router :as router]
            [ampere.utils :as utils]
            [ampere.middleware :as middleware]
            [ampere.db :as db]))

(def app-db "Read-only version of app-db." (cell= db/app-db))

(def dispatch router/dispatch)
(def dispatch-sync router/dispatch-sync)

(def clear-event-handlers! handlers/clear-handlers!)

(def pure middleware/pure)
(def debug middleware/debug)
(def undoable middleware/undoable)
(def path middleware/path)
(def enrich middleware/enrich)
(def trim-v middleware/trim-v)
(def after middleware/after)
(def log-ex middleware/log-ex)
(def vfsm middleware/vfsm)

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
