(ns freactive.macros
  "DEPRECATED use carbon.rx instead"
  (:refer-clojure :exclude [dosync]))

(defmacro rx [& body]
  `(freactive.core/rx* (fn [] ~@body)))

(defmacro no-rx [& body]
  `(binding [freactive.core/*rx* nil]
     ~@body))

(defmacro dosync [& body]
  `(freactive.core/dosync* (fn [] ~@body)))