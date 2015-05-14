(ns ampere.undo
  (:refer-clojure :exclude [dosync])
  (:require [tailrecursion.javelin :refer [cell] :refer-macros [cell= dosync]]
            [ampere.utils :refer [warn]]
            [ampere.db :refer [app-db]]
            [ampere.handlers :as handlers]))

;;; ## History

(def ^:private max-undos "maximum number of undo states maintained" (atom 50))
(defn set-max-undos! [n] (reset! max-undos n))

(def ^:private undo-list "a list of history states" (cell (list)))
(def ^:private redo-list "a list of future states, caused by undoing" (cell (list)))

(defn- clear-undos! [] (reset! undo-list (list)))
(defn- clear-redos! [] (reset! redo-list (list)))

(defn clear-history! []
  (dosync
   (clear-undos!)
   (clear-redos!)))

(defn store-now!
  "stores the value currently in app-db, so the user can later undo"
  [explanation]
  (dosync
    (clear-redos!)
    (swap! undo-list #(take @max-undos (conj % {:db          @app-db
                                                :explanation explanation})))))

(def undos? (cell= (pos? (count undo-list))))
(def redos? (cell= (pos? (count redo-list))))

(def undo-explanations
  "list of undo descriptions"
  (cell= (map :explanation undo-list)))

;;; ## Event handlers

(defn- dodo
  "Pass `from` ← `undo-list` and `to` ← `redo-list` to undo,
   and vice versa to redo."
  [from to]
  (dosync
   (let [u @from]
     (swap! to conj {:db @app-db})
     (swap! from pop)
     (reset! app-db (-> u peek :db)))))

(defn- dodo-n
  "undo/redo until we reach n or run out of undos/redos"
  [from to n]
  (when (and (pos? n) undos?)
    (dodo from to)
    (recur from to (dec n))))

;;; not a pure handler
;;; usage:  (dispatch [:undo n])  n is optional, defaults to 1
(handlers/register-base
 :undo
 (fn handler
   [_ [_ n]]
   (if-not undos?
     (warn "ampere: you did a (dispatch [:undo]), but there is nothing to undo.")
     (dodo-n undo-list redo-list (or n 1)))))

;;; not a pure handler
;;; usage:  (dispatch [:redo n])
;;; if n absent, defaults to 1
(handlers/register-base
 :redo
 (fn handler
   [_ [_ n]]
   (if-not (redos?)
     (warn "ampere: you did a (dispatch [:redo]), but there is nothing to redo.")
     (dodo-n redo-list undo-list (or n 1)))))

;;; not a pure handler
;;; usage:  (dispatch [:purge-redo])
(handlers/register-base
 :purge-redos
 (fn handler
   [_ _]
   (if-not (redos?)
     (warn "ampere: you did a (dispatch [:purge-redos]), but there is nothing to redo.")
     (clear-redos!))))
