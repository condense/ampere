(ns todomvc.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require [goog.events :as events]
            [ampere.core :refer [dispatch dispatch-sync]]
            [secretary.core :as secretary]
            [todomvc.handlers]
            [todomvc.subs :as subs]

            [tailrecursion.hoplon :as h :include-macros true]
            [todomvc.views.hoplon]

            [om.core :as om :include-macros true]
            [ampere.adapters.om]
            [todomvc.views.om]

            [reagent.core]
            [ampere.adapters.reagent]
            [todomvc.views.reagent])
  (:import [goog History]
           [goog.history EventType]
           [goog Uri]))


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
  (dispatch-sync [:initialise-db])
  (let [app (.getElementById js/document "app")
        view (.. Uri (parse js/window.location) (getParameterValue "view"))]
    (case view
      "om" (do
             (ampere.adapters.om/init!)
             (om/root todomvc.views.om/todo-app {}
                      {:target     app
                       :instrument ampere.adapters.om/instrument
                       :opts       {:cells {:todos           subs/todos
                                            :completed-count subs/completed-count}}}))
      "hoplon" (h/replace-children! app (todomvc.views.hoplon/todo-app))
      "reagent" (do
                  (ampere.adapters.reagent/init!)
                  (reagent.core/render [todomvc.views.reagent/todo-app] app))
      (h/replace-children!
        app (h/div
              (h/h1 "Unknown view: " view)
              (h/h2 "Try ?view="
                    (h/ul (for [v ["om" "hoplon" "reagent"]] (h/li v)))))))))

(main)
