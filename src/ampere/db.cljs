(ns ampere.db
  "Application State"
  (:require [carbon.rx :refer-macros [$]]))

(def ^:dynamic app-db
  "Should not be referenced directly by application code.
   Passed for read to subscriptions, for updates to event handlers."
  ($ {}))