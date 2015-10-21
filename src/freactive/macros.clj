(ns freactive.macros)

(defmacro rx [& body]
  `(freactive.core/rx*
    (fn []
      ~@body)
    true))

(defmacro eager-rx [& body]
  `(freactive.core/rx*
    (fn []
      ~@body)
    false))
