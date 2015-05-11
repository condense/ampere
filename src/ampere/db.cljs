(ns ampere.db
  (:require [tailrecursion.javelin :refer [cell]]))

(def app-db
  "## Application State
   Should not be accessed directly by application code.
   Read access goes through Javelin cells,
   read-only root cell is available as `ampere.core/app-db`
   Updates via event handlers."
  (cell {}))