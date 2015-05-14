(ns todomvc.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require [goog.events :as events]
            [om.core :as om :include-macros true]
            [ampere.core :refer [dispatch dispatch-sync]]
            [ampere.adapters.om :as adapter]
            [secretary.core :as secretary]
            [todomvc.handlers]
            [todomvc.subs :as subs]
            [todomvc.views])
  (:import [goog History]
           [goog.history EventType]))


(enable-console-print!)

;; -- Routes and History ------------------------------------------------------

(defroute "/" [] (dispatch [:set-showing :all]))
(defroute "/:filter" [filter] (dispatch [:set-showing (keyword filter)]))

(def history
  (doto (History.)
    (events/listen EventType.NAVIGATE
                   (fn [event] (secretary/dispatch! (.-token event))))
    (.setEnabled true)))


;; -- Entry Point -------------------------------------------------------------

(defn ^:export main
  []
  (adapter/init!)
  (dispatch-sync [:initialise-db])
  (om/root todomvc.views/todo-app {}
           {:target     (.getElementById js/document "app")
            :instrument adapter/instrument
            :opts {:cells {:todos subs/todos
                           :completed-count subs/completed-count}}}))

(main)
