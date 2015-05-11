(ns ^:figwheel-always ampere.example
  (:require [om.core :as om :include-macros true]
            [sablono.core :refer-macros [html]]
            [cljs.core.match :refer-macros [match]]
            [tailrecursion.javelin :refer [cell] :refer-macros [cell=]]
            [ampere.core :as a]))

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

;;; Event Handlers, FSMs should fit fine

(defn index-wrbulkaction-fsm
  "
  State machine representing the interactions through selecting,
  confirming and processing bulk actions from the Index page.
  "
  [{{:keys [state] :as current-state} :wrbulkaction :as db} [_ event data]]
  (assoc db :wrbulkaction
         (match [state event]
           [_ :START] (do ; put any synchronous action right inside handler
                        (print ::START!)
                        {:state :closed})
           [:closed :open] {:state :opened :modal :open}    ; or make pure state transform
           [:opened :dismiss] {:state :closed :modal :closed}
           [:opened :cancel] {:state :closed :modal :closed}
           [:opened :save] (do
                             ; and asynchronous actions performed by event emit
                             ; put event emitting in any callback which should affect app
                             (js/setTimeout
                              #(a/dispatch [:wrbulkaction
                                            (rand-nth [:success :error])
                                            (rand-int 100)])
                              1000)
                             {:state   :loading
                              :modal   :open})
           [:loading :cancel] (do
                                (print ::cancel-post)
                                {:state :closed :modal :closed})
           [:loading :success] (do
                                 (print ::reset-form)
                                 (print ::update-state data)
                                 {:state   :closed
                                  :modal   :closed})
           [:loading :error] (do
                               (a/dispatch [:show-errors data])
                               {:state   :errors :modal :open
                                :errors  data})
           :else current-state)))

;;; Register handlers, after that call them in any part of app
;;; with (ampere.core/dispatch [:event-id params ...])

(a/register-handler :wrbulkaction index-wrbulkaction-fsm)
(a/register-handler :init (fn [db _] (a/dispatch [:wrbulkaction :START]) db))
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
                         :loading      false}))]))))

;;; Run

(a/dispatch [:init])
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
