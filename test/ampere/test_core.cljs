(ns ampere.test-core
  (:require [cljs.test :refer-macros [is deftest async]]
            [ampere.core :as ampere]))


(deftest test-testing-works
  (is (= 1 1)))