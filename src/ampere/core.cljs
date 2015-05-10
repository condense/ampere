(ns ampere.core
  (:require [ampere.handlers :as handlers]
            [ampere.router :as router]
            [ampere.utils :as utils]
            [ampere.middleware :as middleware]))

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

;; ALPHA - EXPERIMENTAL MIDDLEWARE
(def on-changes middleware/on-changes)

;; --  Logging -----
;; re-frame uses the logging functions: warn, log, error, group and groupEnd
;; By default, these functions map directly to the js/console implementations
;; But you can override with your own (set or subset):
;;   (set-loggers!  {:warn  my-warn   :log  my-looger ...})
(def set-loggers! utils/set-loggers!)

;; --  Convenience API -------

;; Almost 100% of handlers will be pure, so make it easy to
;; register with "pure" middleware in the correct (left-hand-side) position.
(defn register-handler
  ([id handler]
   (handlers/register-base id pure handler))
  ([id middleware handler]
   (handlers/register-base id [pure middleware] handler)))