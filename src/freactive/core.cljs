(ns freactive.core
  "## Ampere reactive mechanics

   Any object can be reactive *source*, as long as it satisfies following requirements:

   1. It implements `IDeref` protocol and calls `(freactive.core/register this)` on deref (with itself as an argument).
   2. It is `IWatchable`.

   That's all. `freactive.core/atom` creates an instance of regular `atom` and redefines its `IDeref` implementation to meet 1st requirement.

   `ReactiveExpression` (created by `rx*` function or `rx` macros) is both *source* and `IReactiveExpression`,
   which means that it watches its own *sources* derefed in `getter` and computes & caches new value when they change.

   # TODO:

   * Research autodispose possibilities further.
   * Move code under ampere ns, because nothing left from initial freactive.core."
  (:refer-clojure :exclude [atom]))

(defprotocol IReactive "Marker protocol for sanity checks (like in `ampere.middleware/pure`)")

(defprotocol IReactiveExpression
  (compute [_])
  (add-source [_ source])
  (remove-source [_ source])
  (dispose [_]))

(def ^:dynamic *rx* nil)

(defn register [source]
  (when *rx* (add-source *rx* source)))

(defn with-rx [rx f]
  (binding [*rx* rx] (f)))

(defn atom [x & m]
  (specify! (apply cljs.core/atom x m)
            IReactive
            IDeref
            (-deref [this]
              (register this)
              (.-state this))))

(deftype ReactiveExpression [^:mutable state getter lazy? teardown setter meta validator ^:mutable watches ^:mutable sources]
  IReactive

  Object
  (equiv [this other] (-equiv this other))

  IEquiv
  (-equiv [o other] (identical? o other))

  IDeref
  (-deref [this]
    (register this)
    (when (= state ::none) (compute this))
    state)

  IReactiveExpression
  (compute [this]
    (let [old-value state
          new-value (with-rx this getter)]
      (when-not (and lazy? (= old-value new-value))
        (set! state new-value)
        (-notify-watches this old-value new-value))))
  (add-source [this source]
    (set! sources (conj sources source))
    (add-watch source this #(when (not= %3 %4) (compute this)))
    this)
  (remove-source [this source]
    (set! sources (disj sources source))
    (remove-watch source this)
    this)
  (dispose [this]
    (when (empty? watches)
      (doseq [source sources]
        (remove-source this source))
      (when teardown (teardown this))
      (set! state ::none)))

  IMeta
  (-meta [_] meta)

  IWatchable
  (-notify-watches [this oldval newval]
    (doseq [[key f] watches]
      (f key this oldval newval)))
  (-add-watch [this key f]
    (set! watches (assoc watches key f))
    this)
  (-remove-watch [this key]
    (set! watches (dissoc watches key))
    (dispose this)
    this)

  IHash
  (-hash [this] (goog/getUid this))

  IPrintWithWriter
  (-pr-writer [_ writer opts]
    (-write writer "#<RLens: ")
    (pr-writer state writer opts)
    (-write writer ">"))

  IReset
  (-reset! [_ new-value]
    (assert setter "Can't reset lens w/o setter")
    (when-not (nil? validator)
      (assert (validator new-value) "Validator rejected reference state"))
    (setter new-value)
    new-value)

  ISwap
  (-swap! [this f]
    (reset! this (f state)))
  (-swap! [this f x]
    (reset! this (f state x)))
  (-swap! [this f x y]
    (reset! this (f state x y)))
  (-swap! [this f x y xs]
    (reset! this (apply f state x y xs))))

;; FIXME Ugly API because of backward compatibility with Freactive

(defn rx*
  ([getter] (rx* getter true nil))
  ([getter lazy?] (rx* getter lazy? nil))
  ([getter lazy? teardown & {:keys [meta validator setter]}]
   (ReactiveExpression. ::none                                           ; state
                        getter lazy? teardown setter meta validator
                        {}                                               ; watches
                        #{}                                              ; sources
)))

(defn cursor* [parent korks]
  (let [korks (if (coll? korks) korks [korks])]
    (rx* #(get-in @parent korks)
         true nil
         :setter #(swap! parent assoc-in korks %))))

(def cursor (memoize cursor*))