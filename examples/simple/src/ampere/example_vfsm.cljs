(ns ampere.example-vfsm
  (:require-macros [vfsm.graphml :refer [compile-spec-from-resources]])
  (:require [ampere.core :as a]))

(enable-console-print!)

(defn evt= [& ks]
  (fn [db]
    ((set ks) (get-in db [:event 0]))))

(defn close-modal [ctx db]
  (assoc db :modal :closed))

(defn open-modal [ctx db]
  (assoc db :modal :open))

(defn show-loading [ctx db]
  (assoc db :loading true))

(defn hide-loading [ctx db]
  (assoc db :loading false))

(defn reset-form [c d]
  (print ::reset-form)
  (assoc d :data (get-in d [:event 1])))

(defn cancel-post [c d]
  (print ::cancel-post)
  d)

(defn show-errors [c d]
  (a/dispatch [:show-errors (get-in d [:event 1])])
  d)

(defn set-errors [c d]
  (assoc d :errors (get-in d [:event 1])))

(defn post [c d]
  (js/setTimeout
    #(a/dispatch [:wrbulkaction
                  (rand-nth [:success :error])
                  (rand-int 100)])
    1000)
  d)

(def spec (compile-spec-from-resources "example.graphml"))