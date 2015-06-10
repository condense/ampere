(ns ampere.undo
  (:refer-clojure :exclude [dosync])
  (:require-macros [freactive.macros :refer [rx]])
  (:require [freactive.core :as r]
            [ampere.utils :refer [warn]]
            [ampere.db :refer [app-db]]
            [ampere.handlers :as handlers]))

;;; ## History

(def ^:private max-undos "Maximum number of undo states maintained." (atom 50))
(defn set-max-undos! [n] (reset! max-undos n))

(def ^:private undo-list "A list of history states." (r/atom (list)))
(def ^:private redo-list
  "A list of future states, caused by undoing."
  (r/atom (list)))

(defn- clear-undos! [] (reset! undo-list (list)))
(defn- clear-redos! [] (reset! redo-list (list)))

(defn clear-history! []
  (clear-undos!)
  (clear-redos!))

(defn store-now!
  "Stores the value currently in app-db, so the user can later undo."
  [explanation]
  (clear-redos!)
  (swap! undo-list #(take @max-undos (conj % {:db          @app-db
                                              :explanation explanation}))))

(def undos? (rx (pos? (count @undo-list))))
(def redos? (rx (pos? (count @redo-list))))

(def undo-explanations
  "List of undo descriptions."
  (rx (map :explanation @undo-list)))

;;; ## Event handlers

(defn- dodo
  "Pass `from` ← `undo-list` and `to` ← `redo-list` to undo,
   and vice versa to redo."
  [from to]
  (let [u @from]
    (swap! to conj {:db @app-db})
    (swap! from pop)
    (reset! app-db (-> u peek :db))))

(defn- dodo-n
  "Undo/redo until we reach n or run out of undos/redos."
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
