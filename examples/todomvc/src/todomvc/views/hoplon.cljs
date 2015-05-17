(ns todomvc.views.hoplon
  (:require [tailrecursion.hoplon :as h :include-macros true]
            [tailrecursion.javelin :as j :include-macros true]
            [ampere.core :refer [dispatch]]
            [todomvc.subs :as subs]))

(h/defelem todo-input [{:keys [title on-save on-stop] :as attrs} _]
  (let [val (j/cell (and title @title))
        stop #(do (reset! val "")
                  (if on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
               (if-not (empty? v) (on-save v))
               (stop))]
    (h/input (merge attrs {:type    "text"
                           :value   val
                           :blur    save
                           :input   #(reset! val (-> % .-target .-value))
                           :keydown #(case (.-which %)
                                      13 (save)
                                      27 (stop)
                                      nil)}))))

(h/defelem todo-edit [attrs _]
  (j/with-let [e (todo-input attrs)]
    (h/when-dom e #(.focus e))))

(h/defelem props-for [{:keys [filter filter-kw]} [txt]]
  (h/a :class (j/cell= {:selected (= filter-kw filter)})
       :href (str "#/" (name filter-kw))
       txt))

(h/defelem stats-footer [_ _]
  (j/cell-let [[active done filter] subs/footer-stats]
    (let [items (j/cell= (case active 1 "item" "items"))]
      (h/footer
        :id "footer"
        (h/div
          (h/span
            :id "todo-count"
            (h/strong (h/text  "~{active}"))
            (h/text " ~{items} left"))
          (h/ul
            :id "filters"
            (h/li (props-for :filter filter :filter-kw :all "All"))
            (h/li (props-for :filter filter :filter-kw :active "Active"))
            (h/li (props-for :filter filter :filter-kw :done "Completed")))
          (h/button
            :id "clear-completed"
            :click #(dispatch [:clear-completed])
            :toggle (j/cell= (pos? done))
            (h/text "Clear completed ~{done}")))))))

(h/defelem todo-item [{:keys [todo]} _]
  (let [editing (j/cell false)]
    (j/cell-let [{:keys [id done title]} todo]
      (h/li :class (j/cell= {:completed done
                             :editing editing})
            (h/div
              :class "view"
              (h/input
                :class "toggle"
                :type "checkbox"
                :checked done
                :change #(dispatch [:toggle-done @id]))
              (h/label :dblclick #(reset! editing true) title)
              (h/button :class "destroy"
                        :click #(dispatch [:delete-todo @id])))
            (todo-edit
              :toggle editing
              :class "edit"
              :title title
              :on-save #(dispatch [:save @id %])
              :on-stop #(reset! editing false))))))

(h/defelem todo-list [_ _]
  (h/ul :id "todo-list"
        (h/loop-tpl :bindings [todo subs/visible-todos]
          (todo-item :todo todo))))

(h/defelem todo-app [_ _]
  (h/div
    (h/section
      :id "todoapp"
      (h/header
        :id "header"
        (h/h1 "todos")
        (todo-input :id          "new-todo"
                    :placeholder "What needs to be done?"
                    :on-save     #(dispatch [:add-todo %])))
      (h/div
        :toggle (j/cell= (seq subs/todos))
        (h/section
          :id "main"
          (h/input
            :id "toggle-all"
            :type "checkbox"
            :checked (j/cell= (pos? subs/completed-count))
            :change #(dispatch [:complete-all-toggle]))
          (h/label :for "toggle-all" "Mark all as complete")
          (todo-list))
        (stats-footer)))
    (h/footer
      :id "info"
      (h/p "Double-click to edit a todo"))))