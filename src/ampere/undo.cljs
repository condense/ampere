(ns ampere.undo
  (:refer-clojure :exclude [dosync])
  (:require [tailrecursion.javelin :refer [cell] :refer-macros [cell= dosync]]
            [ampere.utils :refer [warn]]
            [ampere.db :refer [app-db]]
            [ampere.handlers :as handlers]))

;;; ## History

(def ^:private max-undos "maximum number of undo states maintained" (atom 50))
(defn set-max-undos!
  [n]
  (reset! max-undos n))


(def ^:private undo-list "a list of history states" (cell []))
(def ^:private redo-list "a list of future states, caused by undoing" (cell []))

;;; ## Explainations

;;; Each undo has an associated explanation which can be displayed to the user.
;;; Seems really ugly to have mirrored vectors, but ...
;;; the code kinda falls out when you do. I'm feeling lazy.
(def ^:private app-explain "mirrors app-db" (cell ""))
(def ^:private undo-explain-list "mirrors undo-list" (cell []))
(def ^:private redo-explain-list "mirrors redo-list" (cell []))

(defn- clear-undos!
  []
  (dosync
   (reset! undo-list [])
   (reset! undo-explain-list [])))

(defn- clear-redos!
  []
  (dosync
   (reset! redo-list [])
   (reset! redo-explain-list [])))

(defn clear-history!
  []
  (dosync
   (clear-undos!)
   (clear-redos!)
   (reset! app-explain "")))

(defn store-now!
  "stores the value currently in app-db, so the user can later undo"
  [explanation]
  (clear-redos!)
  (dosync
   (reset! undo-list (vec (take
                           @max-undos
                           (conj @undo-list @app-db))))
   (reset! undo-explain-list (vec (take
                                   @max-undos
                                   (conj @undo-explain-list @app-explain))))
   (reset! app-explain explanation)))

(def undos? (cell= (pos? (count undo-list))))
(def redos? (cell= (pos? (count redo-list))))

(def undo-explanations
  "return list of undo descriptions or empty list if no undos"
  (cell=
   (if undos?
     (conj undo-explain-list app-explain)
     [])))

;;; ## Event handlers

(defn- undo
  [undos cur redos]
  (dosync
   (let [u @undos
         r (cons @cur @redos)]
     (reset! cur (last u))
     (reset! redos r)
     (reset! undos (pop u)))))

(defn- undo-n
  "undo until we reach n or run out of undos"
  [n]
  (when (and (pos? n) undos?)
    (undo undo-list app-db redo-list)
    (undo undo-explain-list app-explain redo-explain-list)
    (recur (dec n))))

;;; not a pure handler
;;; usage:  (dispatch [:undo n])  n is optional, defaults to 1
(handlers/register-base
 :undo
 (fn handler
   [_ [_ n]]
   (if-not undos?
     (warn "ampere: you did a (dispatch [:undo]), but there is nothing to undo.")
     (undo-n (or n 1)))))

(defn- redo
  [undos cur redos]
  (dosync
   (let [u (conj @undos @cur)
         r @redos]
     (reset! cur (first r))
     (reset! redos (rest r))
     (reset! undos u))))

(defn- redo-n
  "redo until we reach n or run out of redos"
  [n]
  (when (and (pos? n) (redos?))
    (redo undo-list app-db redo-list)
    (redo undo-explain-list app-explain redo-explain-list)
    (recur (dec n))))

;;; not a pure handler
;;; usage:  (dispatch [:redo n])
;;; if n absent, defaults to 1
(handlers/register-base
 :redo
 (fn handler
   [_ [_ n]]
   (if-not (redos?)
     (warn "ampere: you did a (dispatch [:redo]), but there is nothing to redo.")
     (redo-n (or n 1)))))

;;; not a pure handler
;;; usage:  (dispatch [:purge-redo])
(handlers/register-base
 :purge-redos
 (fn handler
   [_ _]
   (if-not (redos?)
     (warn "ampere: you did a (dispatch [:purge-redos]), but there is nothing to redo.")
     (clear-redos!))))
