(ns ampere.db
  "Application State"
  (:require [reagent.core :refer [atom]]))

(def app-db
  "Should not be referenced directly by application code.
   Passed for read to subscriptions, for updates to event handlers."
  (atom {}))