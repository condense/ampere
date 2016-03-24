(ns ampere.test-runner
  (:require [jx.reporter.karma :as karma :include-macros true]
            ampere.test-core
            ampere.test-router))


(defn ^:export run [karma]
  (karma/run-tests
    karma
    'ampere.test-core
    'ampere.test-router))
