(ns ^:figwheel-always ampere.example
  (:require [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [tailrecursion.javelin :refer [cell] :refer-macros [cell=]]))

(enable-console-print!)

(defonce app-state (atom {:text "Hello world!"}))

(defonce x (cell 0))
(defonce y (cell= (if (> 2 x) x -1)))
(defonce z (cell= (print y)))

(om/root
 (fn [data owner]
   (reify om/IRender
     (render [_]
       (html [:div (str @x) " " (str @y) [:button {:on-click #(swap! x inc)} "Inc"]]))))
 app-state
 {:target (. js/document (getElementById "app"))})



(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
