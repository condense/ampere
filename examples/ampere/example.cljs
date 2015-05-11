(ns ^:figwheel-always ampere.example
  (:require [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [cljs.core.match :refer-macros [match]]
            [tailrecursion.javelin :refer [cell] :refer-macros [cell=]]
            [ampere.core :as a]
            [ampere.example-vfsm :refer [spec]]))

(enable-console-print!)

;;; Ampere does not use subscriptions, because Javelin cells + CLJS namespacing
;;; fully cover their functionality.
;;; Formulas below usually reside in their own namespace and plays role of
;;; subscriptions.
;;; The beauty is that you can run chains of transformations,
;;; efficiently eliminating recomputation of unchanged branches.
;;; E.g.
;;; (def items (cell= (:items a/app-db))) ; cheap to get map value
;;; (def sorted-items (cell= (take 20 (sort items)))) ; expensive, but recomputed only on items change, not every app-db touch

(def wrbulkaction (cell= (:wrbulkaction a/app-db)))

;;; Register handlers, after that call them in any part of app
;;; with (ampere.core/dispatch [:event-id params ...])

(a/register-handler :wrbulkaction [a/trim-v (a/path :wrbulkaction) (a/vfsm {})] spec)
(a/register-handler :show-errors (fn [db [_ data]] (js/alert (str "Error: " data)) db))

(defn ConfirmAction [_ _]
  (om/component
   (html [:div "ConfirmAction"])))

(defn Modal [{:keys [modal-header modal-body
                     on-save on-cancel on-dismiss
                     ok-copy loading]} _]
  (om/component
   (html
    [:div
     [:h1 modal-header]
     [:div modal-body]
     [:div
      (when loading "Loading...")]
     [:div
      [:button {:on-click on-save} ok-copy]
      [:button {:on-click on-cancel} "Cancel"]
      [:button {:on-click on-dismiss} "Dismiss"]]])))

;;; :wrbulkaction comes from subscription in :opts
(defn ActionSelect [{{:keys [modal]} :wrbulkaction} owner]
  (om/component
   (let [fsm-step #(a/dispatch [:wrbulkaction %])]
     (html [:div
            [:button {:on-click #(fsm-step :open)} "Go"]
            (if (= modal :open)
              (om/build Modal
                        {:modal-header "Are you sure?"
                         :modal-body   (om/build ConfirmAction nil)
                         :on-save      #(fsm-step :save)
                         :on-cancel    #(fsm-step :cancel)
                         :on-dismiss   #(fsm-step :dismiss)
                         :ok-copy      "Ok"
                         :loading      false}))
            [:div [:pre (pr-str @a/app-db)]]]))))

;;; Run

;;; Note including :instrument and providing subscriptions in :opts
(om/root ActionSelect {}
         {:target (. js/document (getElementById "app"))
          :opts {:cells {:wrbulkaction wrbulkaction}}
          :instrument a/instrument})

;;; Aux
(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
