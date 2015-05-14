(ns todomvc.subs
  (:require [tailrecursion.javelin :refer-macros [cell=]]
            [ampere.core :refer [app-db]]))

;;; Helpers

(defn filter-fn-for
  [showing-kw]
  (case showing-kw
    :active (complement :done)
    :done :done
    :all identity
    nil))

;;; Cells replace subscriptions with very succint and readable code :-)

(def todos (cell= (vals (:todos app-db))))
(def showing (cell= (:showing app-db)))
(def visible-todos (cell= (when-let [filter-fn (filter-fn-for showing)]
                            (filter filter-fn todos))))
(def completed-count (cell= (count (filter :done todos))))
(def footer-stats (cell= [(- (count todos) completed-count) completed-count showing]))