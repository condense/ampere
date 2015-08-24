(ns ampere.db
  "Application State"
  (:require [freactive.core :refer [atom]]))

(def ^:dynamic app-db
  "Should not be referenced directly by application code.
   Passed for read to subscriptions, for updates to event handlers."
  (atom {}))