(ns ampere.core
  (:require [om.core :as om :include-macros true]
            [tailrecursion.javelin :refer-macros [cell=]]
            [ampere.handlers :as handlers]
            [ampere.router :as router]
            [ampere.utils :as utils]
            [ampere.middleware :as middleware]
            [ampere.db :as db]))

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
;; ampere uses the logging functions: warn, log, error, group and groupEnd
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

;;

(defn Wrapper [[f cursor m] owner]
  (reify
    om/IInitState
    (init-state [_]
      {::id (gensym)})
    om/IWillMount
    (will-mount [_]
      (let [id (om/get-state owner ::id)
            f (fn [k _ _ _ v] (om/set-state! owner k v))]
        (doseq [[k v] (get-in m [:opts :sub])]
          (add-watch v id (partial f k)))))
    om/IWillUnmount
    (will-unmount [_]
      (let [id (om/get-state owner ::id)]
        (doseq [[_ v] (get-in m [:opts :sub])]
          (remove-watch v id))))
    om/IRenderState
    (render-state [_ state]
      (om/build* f (merge cursor state) m))))

(defn instrument [& args]
  (om/build* Wrapper args))

(def app-db "Read-only version of app-db." (cell= db/app-db))