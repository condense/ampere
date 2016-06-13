(ns freactive.core
  "DEPRECATED use carbon.rx instead"
  (:refer-clojure :exclude [atom]))

(defprotocol IReactiveSource
  (get-rank [_])
  (add-sink [_ sink])
  (remove-sink [_ sink])
  (get-sinks [_]))

(defprotocol IReactiveExpression
  (compute [_])
  (gc [_])
  (add-source [_ source])
  (remove-source [_ source]))

(def ^:dynamic *rx* nil)                                    ; current parent expression
(def ^:dynamic *rank* nil)                                  ; highest rank met during expression compute
(def ^:dynamic *dirty-sinks* nil)                           ; subject to `compute`
(def ^:dynamic *dirty-sources* nil)                         ; subject to `gc`
(def ^:dynamic *provenance* [])

(defn compare-by [keyfn]
  (fn [x y]
    (compare (keyfn x) (keyfn y))))

(defn rank-hash [x]
  [(get-rank x) (hash x)])

(def empty-queue (sorted-set-by (compare-by rank-hash)))

(defn propagate*
  "Recursively compute all dirty sinks in the `queue` and return all visited sources to clean."
  [queue]
  (binding [*rx* nil *rank* nil]                            ; try to be foolproof
    (loop [queue queue dirty '()]
      (if-let [x (first queue)]
        (let [queue (disj queue x)
              dirty (conj dirty x)]
          (if (= @x (compute x))
            (recur queue dirty)
            (recur (into queue (get-sinks x)) dirty)))
        dirty))))

(defn clean
  "Recursively garbage collect all disconnected sources in the `queue`"
  [queue]
  (doseq [source queue]
    (gc source)))

(defn propagate
  "Recursively compute all dirty sources in the `queue` and clean visited sources."
  [queue]
  (let [dirty (propagate* queue)]
    (if *dirty-sources*
      (vswap! *dirty-sources* into dirty)
      (clean dirty))))

(defn register [source]
  (when *rx*                                                ; *rank* too
    (add-sink source *rx*)
    (add-source *rx* source)
    (vswap! *rank* max (get-rank source))))

(defn dosync* [f]
  (let [sinks (or *dirty-sinks* (volatile! empty-queue))
        sources (or *dirty-sources* (volatile! empty-queue))
        result (binding [*dirty-sinks* sinks
                         *dirty-sources* sources]
                 (f))]
    (binding [*dirty-sources* sources]
      (propagate @sinks))
    (when-not *dirty-sources*
      (clean @sources))
    result))

(deftype ReactiveExpression [getter setter teardown meta validator
                             ^:mutable state ^:mutable watches
                             ^:mutable rank ^:mutable sources ^:mutable sinks]

  Object
  (equiv [this other] (-equiv this other))

  IEquiv
  (-equiv [o other] (identical? o other))

  IDeref
  (-deref [this]
    (when (= state ::thunk) (compute this))
    (register this)
    state)

  IReactiveSource
  (get-rank [_] rank)
  (add-sink [_ sink] (set! sinks (conj sinks sink)))
  (remove-sink [_ sink] (set! sinks (disj sinks sink)))
  (get-sinks [_] sinks)

  IReactiveExpression
  (compute [this]
    (doseq [source sources]
      (remove-sink source this))
    (set! sources #{})
    (let [old-value state
          r (volatile! 0)
          new-value (binding [*rx* this
                              *rank* r
                              *provenance* (conj *provenance* this)]
                      (getter))]
      (set! rank (inc @r))
      (when (not= old-value new-value)
        (set! state new-value)
        (-notify-watches this old-value new-value))
      new-value))
  (gc [this]
    (if *dirty-sources*
      (vswap! *dirty-sources* conj this)
      (when (and (empty? sinks) (empty? watches))
        (doseq [source sources]
          (remove-sink source this)
          (when (satisfies? IReactiveExpression source)
            (gc source)))
        (set! sources #{})
        (set! state ::thunk)
        (when teardown (teardown)))))
  (add-source [_ source]
    (set! sources (conj sources source)))
  (remove-source [_ source]
    (set! sources (disj sources source)))

  IMeta
  (-meta [_] meta)

  IWatchable
  (-notify-watches [this oldval newval]
    (doseq [[key f] watches]
      (f key this oldval newval)))
  (-add-watch [this key f]
    (when (= state ::thunk) (compute this))
    (set! watches (assoc watches key f))
    this)
  (-remove-watch [this key]
    (set! watches (dissoc watches key))
    (gc this)
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
    (dosync* #(setter new-value))
    new-value)

  ISwap
  (-swap! [this f]
    (when (= state ::thunk) (compute this))
    (reset! this (f state)))
  (-swap! [this f x]
    (when (= state ::thunk) (compute this))
    (reset! this (f state x)))
  (-swap! [this f x y]
    (when (= state ::thunk) (compute this))
    (reset! this (f state x y)))
  (-swap! [this f x y xs]
    (when (= state ::thunk) (compute this))
    (reset! this (apply f state x y xs))))

(defn watch [_ source o n]
  (when (not= o n)
    (if *dirty-sinks*
      (vswap! *dirty-sinks* into (get-sinks source))
      (->> source get-sinks (into empty-queue) propagate))))

(defn atom [x & m]
  (let [sinks (volatile! #{})]
    (specify! (apply cljs.core/atom x m)

      IReactiveSource
      (get-rank [_] 0)
      (add-sink [_ sink] (vswap! sinks conj sink))
      (remove-sink [_ sink] (vswap! sinks disj sink))
      (get-sinks [_] @sinks)

      IDeref
      (-deref [this]
        (register this)
        (add-watch this ::rx watch)
        (.-state this)))))

;; FIXME Ugly API because of backward compatibility with Freactive

(defn rx*
  ([getter] (rx* getter nil nil))
  ([getter _] (rx* getter nil nil))
  ([getter _ teardown & {:keys [meta validator setter]}]
   (ReactiveExpression. getter setter teardown meta validator ::thunk {} 0 #{} #{})))

(defn cursor* [parent korks]
  (let [korks (if (coll? korks) korks [korks])]
    (rx* #(get-in @parent korks)
         true nil
         :setter #(swap! parent assoc-in korks %))))

(def cursor (memoize cursor*))