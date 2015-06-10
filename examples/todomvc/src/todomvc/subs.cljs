(ns todomvc.subs
  (:require-macros [freactive.macros :refer [rx]])
  (:require [freactive.core :refer [cursor]]))

;;; Helpers

(defn filter-fn-for
  [showing-kw]
  (case showing-kw
    :active (complement :done)
    :done :done
    :all identity
    nil))

(defn todos [db _]
  (rx (vals (:todos @db))))

(defn showing [db _]
  (cursor db :showing))

;; FIXME demonstrate how to use static subs to reduce boilerplate and improve perf
(defn visible-todos [db _]
  (let [showing (showing db _)
        todos (todos db _)]
    (rx (when-let [filter-fn (filter-fn-for @showing)]
          (filter filter-fn @todos)))))

(defn completed-count [db _]
  (let [todos (todos db _)]
    (rx (count (filter :done @todos)))))

(defn footer-stats [db _]
  (let [cc (completed-count db _)
        todos (todos db _)
        showing (showing db _)]
    (rx
     (let [cc @cc]
       [(- (count @todos) cc) cc @showing]))))