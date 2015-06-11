(ns todomvc.views.om
  (:require [sablono.core :refer-macros [html]]
            [om.core :as om :include-macros true]
            [ampere.core :refer [dispatch]]
            [ampere.om :refer [observe]]))

(defn todo-input [{:keys [title on-save on-stop] :as props} owner]
  (reify
    om/IInitState
    (init-state [_] {:val title})
    om/IRenderState
    (render-state [_ {:keys [val]}]
      (let [stop #(do (om/set-state! owner :val "")
                      (if on-stop (on-stop)))
            save #(let [v (-> val str clojure.string/trim)]
                    (if-not (empty? v) (on-save v))
                    (stop))]
        (html [:input (merge props
                             {:type        "text"
                              :value       val
                              :on-blur     save
                              :on-change   #(om/set-state!
                                             owner :val (-> % .-target .-value))
                              :on-key-down #(case (.-which %)
                                              13 (save)
                                              27 (stop)
                                              nil)})])))))

(defn todo-edit [props owner]
  (reify
    om/IDidMount
    (did-mount [_] (.focus (om/get-node owner)))
    om/IRender
    (render [_] (om/build todo-input props))))

(defn stats-footer [{:keys [footer-stats]} owner]
  (reify
    om/IRender
    (render [_]
      (let [[active done filter] footer-stats
            props-for (fn [filter-kw txt]
                        [:a {:class (if (= filter-kw filter) "selected")
                             :href (str "#/" (name filter-kw))} txt])]
        (html
         [:footer#footer
          [:div
           [:span#todo-count
            [:strong active] " " (case active 1 "item" "items") " left"]
           [:ul#filters
            [:li (props-for :all "All")]
            [:li (props-for :active "Active")]
            [:li (props-for :done "Completed")]]
           (when (pos? done)
             [:button#clear-completed {:on-click #(dispatch [:clear-completed])}
              "Clear completed " done])]])))))

(defn todo-item [{:keys [id done title]} owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [editing]}]
      (html
       [:li {:class (str (if done "completed ")
                         (if editing "editing"))}
        [:div.view
         [:input.toggle {:type "checkbox"
                         :checked done
                         :on-change #(dispatch [:toggle-done id])}]
         [:label {:on-double-click #(om/set-state! owner :editing true)} title]
         [:button.destroy {:on-click #(dispatch [:delete-todo id])}]]
        (when editing
          (om/build todo-edit
                    {:class   "edit"
                     :title   title
                     :on-save #(dispatch [:save id %])
                     :on-stop #(om/set-state! owner :editing false)}))]))))

(defn todo-list [{:keys [visible-todos]} owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:ul#todo-list
        (om/build-all todo-item visible-todos)]))))

(defn todo-app [_ owner]
  (reify
    om/IRender
    (render [_]
      ;; just for fun let subscribe inside the component
      (let [todos (observe owner ^{:key :super-todos} [:todos])
            completed-count (observe owner [:completed-count])]
        (html
         [:div
          [:section#todoapp
           [:header#header
            [:h1 "todos"]
            (om/build
             todo-input {:id          "new-todo"
                         :placeholder "What needs to be done?"
                         :on-save     #(dispatch [:add-todo %])})]
           (when-not (empty? todos)
             [:div
              [:section#main
               [:input#toggle-all
                {:type      "checkbox"
                 :checked   (pos? completed-count)
                 :on-change #(dispatch [:complete-all-toggle])}]
               [:label {:for "toggle-all"} "Mark all as complete"]
               (om/build todo-list {}
                         {:opts {:subs {:visible-todos [:visible-todos]}}})]
              (om/build stats-footer {}
                        {:opts {:subs {:footer-stats [:footer-stats]}}})])]
          [:footer#info
           [:p "Double-click to edit a todo"]]])))))

