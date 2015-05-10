(ns ampere.db
  (:require [tailrecursion.javelin :refer [cell]]))

;; -- Application State  --------------------------------------------------------------------------
;;
;; Should not be accessed directly by application code
;; Read access goes through subscriptions.
;; Updates via event handlers.
(def app-db (cell {}))