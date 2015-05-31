(ns todomvc.subs
  (:require [reagent.ratom :refer-macros [reaction]]))

;;; Helpers

(defn filter-fn-for
  [showing-kw]
  (case showing-kw
    :active (complement :done)
    :done :done
    :all identity
    nil))

(defn todos [db _]
  (reaction (vals (:todos @db))))

(defn showing [db _]
  (reaction (:showing @db)))

(defn visible-todos [db _]
  (reaction (when-let [filter-fn (filter-fn-for @(showing db _))]
              (filter filter-fn @(todos db _)))))

(defn completed-count [db _]
  (reaction (count (filter :done @(todos db _)))))

(defn footer-stats [db _]
  (reaction
    (let [cc @(completed-count db _)]
      [(- (count @(todos db _)) cc) cc @(showing db _)])))