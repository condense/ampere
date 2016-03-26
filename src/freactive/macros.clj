(ns freactive.macros)

(defmacro rx [& body]
  `(freactive.core/rx* (fn [] ~@body)))

(defmacro no-rx [& body]
  `(binding [freactive.core/*rx* nil]
     ~@body))
