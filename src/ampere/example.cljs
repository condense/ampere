(ns ^:figwheel-always ampere.example
  (:require [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [tailrecursion.javelin :refer [cell] :refer-macros [cell=]]))

(enable-console-print!)

;;; Ampere does not use subscriptions, because Javelin cells + CLJS namespacing
;;; fully cover their functionality.
;;; Formulas below usually reside in their own namespace and plays role of
;;; subscriptions.


;;;
(om/root
 #(om/component (html [:div "OK"])) {}
 {:target (. js/document (getElementById "app"))})

;;; Aux

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
