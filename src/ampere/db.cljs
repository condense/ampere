(ns ampere.db
  "Application State"
  (:require [tailrecursion.javelin :refer [cell]]))

(def app-db
  "Should not be accessed directly by application code.
   Read access goes through Javelin cells,
   read-only root cell is available as `ampere.core/app-db`
   Updates via event handlers."
  (cell {}))