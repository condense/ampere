(ns ampere.test-runner
  (:require [jx.reporter.karma :as karma :include-macros true]
            ampere.test-core))


(defn ^:export run [karma]
  (karma/run-tests
    karma
    'ampere.test-core))
