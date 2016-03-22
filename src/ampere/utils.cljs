(ns ampere.utils
  (:require [clojure.set :refer [difference]]
            [goog.log :as glog]
            [goog.debug.Logger.Level :as lvl])
  (:import [goog.debug Console]))


(defonce *logger*
  (when ^boolean goog.DEBUG
    (.setCapturing (Console.) true)
    (glog/getLogger "ampere")))

;;; ## Logging
;;;
;;; Ampere internally uses a set of logging functions which, by default, make use of goog.log
;;; Use set-loggers! if you want to change this default behaviour.
;;; In production environment, you may want to capture exceptions and POST
;;; them somewhere. To do that, you might want to override the way that exceptions are
;;; handled by overridding "error"
(def default-loggers
  {:fine     #(glog/fine *logger* %)
   :info     #(glog/info *logger* %)
   :warn     #(glog/warning *logger* %)
   :error    #(glog/error *logger* %) })

(def loggers
  "Holds the current set of loggers."
  (atom default-loggers))

(defn set-loggers!
  "Change the set (subset?) of logging functions used by ampere.
  'new-loggers' should be a map which looks like default-loggers"
  [new-loggers]
  (assert (empty? (difference (set (keys new-loggers)) (set (keys default-loggers)))) "Unknown keys in new-loggers")
  (swap! loggers merge new-loggers))

(defn set-logger-level! [level]
  (when *logger*
    (.setLevel *logger*
               (case level
                 :fine lvl/FINE
                 :info lvl/INFO
                 :warn lvl/WARNING
                 :error lvl/SEVERE))))

(defn fine [& args] ((:fine @loggers) (apply str args)))
(defn info [& args] ((:info @loggers) (apply str args)))
(defn warn [& args] ((:warn @loggers) (apply str args)))
(defn error [& args] ((:error @loggers) (apply str args)))

;;; ## Misc

(defn first-in-vector [v]
  (if (vector? v)
    (first v)
    (error "ampere: expected a vector event, but got: " v)))

(defn map-vals [f m]
  (persistent! (reduce-kv (fn [z k v] (assoc! z k (f v))) (transient {}) m)))