(ns freactive.core
  "TODO:
   * guarantee equality of cursors with the same parent & path not by fingerprint, but by caching instances
   * research autodispose possibilities further"
  (:refer-clojure :exclude [atom]))

(def ^:dynamic *sink* nil)

(defprotocol IReactive
  (compute [_]))

(defprotocol ISink
  (invalidate [_])
  (add-source [_ source])
  (remove-source [_ source])
  (dispose [_]))

(defprotocol ISource
  (add-sink [_ sink])
  (remove-sink [_ sink])
  (invalidate-sinks [_]))

(defn add-link [source sink]
  (add-sink source sink)
  (add-source sink source))

(defn remove-link [source sink]
  (remove-sink source sink)
  (remove-source sink source))

(deftype RAtom [state meta validator watches sinks lazy?]
  IReactive
  (compute [_] @state)

  Object
  (equiv [this other]
    (-equiv this other))

  IEquiv
  (-equiv [o other] (identical? o other))

  IDeref
  (-deref [this]
    (when-let [sink *sink*]
      (add-link this sink))
    @state)

  ISource
  (invalidate-sinks [this]
    (doseq [sink @sinks]
      (remove-link this sink)
      (invalidate sink)))
  (add-sink [this sink]
    (vswap! sinks conj sink)
    this)
  (remove-sink [this sink]
    (vswap! sinks disj sink)
    this)

  IMeta
  (-meta [_] meta)

  IWatchable
  (-notify-watches [this oldval newval]
    (doseq [[key f] @watches]
      (f key this oldval newval)))
  (-add-watch [this key f]
    (vswap! watches assoc key f)
    this)
  (-remove-watch [this key]
    (vswap! watches dissoc key)
    this)

  IHash
  (-hash [this] (goog/getUid this))

  IPrintWithWriter
  (-pr-writer [this writer opts]
    (-write writer "#<RAtom: ")
    (pr-writer @state writer opts)
    (-write writer ">"))

  IReset
  (-reset! [this new-value]
    (when-not (nil? validator)
      (assert (validator new-value) "Validator rejected reference state"))
    (let [old-value @state]
      (when-not (and lazy? (= old-value new-value))
        (vreset! state new-value)
        (invalidate-sinks this)
        (-notify-watches this old-value new-value))
      new-value))

  ISwap
  (-swap! [this f]
    (reset! this (f @state)))
  (-swap! [this f x]
    (reset! this (f @state x)))
  (-swap! [this f x y]
    (reset! this (f @state x y)))
  (-swap! [this f x y xs]
    (reset! this (apply f @state x y xs))))

(defn atom
  "Creates and returns an RAtom with an initial value of x and zero or
  more options (in any order):

  :meta metadata-map

  :validator validate-fn

  :lazy? do not notify watches if value hasn't been changed

  If metadata-map is supplied, it will be come the metadata on the
  atom. validate-fn must be nil or a side-effect-free fn of one
  argument, which will be passed the intended new state on any state
  change. If the new state is unacceptable, the validate-fn should
  return false or throw an Error.  If either of these error conditions
  occur, then the value of the atom will not change."
  ([x] (RAtom. (volatile! x) nil nil (volatile! {}) (volatile! #{}) false))
  ([x & {:keys [meta validator lazy?]}] (RAtom. (volatile! x) meta validator (volatile! {}) (volatile! #{}) lazy?)))

(deftype RLens [state getter lazy? teardown setter meta validator watches sinks sources]
  IReactive
  (compute [this]
    (let [old-value @state
          new-value (binding [*sink* this] (getter))]
      (when-not (and lazy? (= old-value new-value))
        (vreset! state new-value)
        (invalidate-sinks this)
        (-notify-watches this old-value new-value))
      new-value))

  Object
  (equiv [this other]
    (-equiv this other))

  IEquiv
  (-equiv [o other]
    (if-let [fp (::fingerprint meta)]
      (= fp (::fingerprint (-meta other)))
      (identical? o other)))

  IDeref
  (-deref [this]
    (when-let [sink *sink*]
      (add-link this sink))
    (if (= @state ::none)
      (compute this)
      @state))

  ISink
  (invalidate [this]
    (compute this))
  (add-source [this source]
    (vswap! sources conj source)
    this)
  (remove-source [this source]
    (vswap! sources disj source)
    this)
  (dispose [this]
    (when (and (empty? @sinks)
               (empty? @watches))
      (doseq [source @sources]
        (remove-sink source this)
        (when (satisfies? ISink source)
          (dispose source)))
      (when teardown (teardown this))))

  ISource
  (invalidate-sinks [this]
    (doseq [sink @sinks]
      (remove-link this sink)
      (invalidate sink))
    (dispose this))
  (add-sink [this sink]
    (vswap! sinks conj sink)
    this)
  (remove-sink [this sink]
    (vswap! sinks disj sink)
    this)

  IMeta
  (-meta [_] meta)

  IWatchable
  (-notify-watches [this oldval newval]
    (doseq [[key f] @watches]
      (f key this oldval newval)))
  (-add-watch [this key f]
    (vswap! watches assoc key f)
    this)
  (-remove-watch [this key]
    (vswap! watches dissoc key)
    (dispose this)
    this)

  IHash
  (-hash [this] (goog/getUid this))

  IPrintWithWriter
  (-pr-writer [this writer opts]
    (-write writer "#<RLens: ")
    (pr-writer @state writer opts)
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
    (reset! this (f @state)))
  (-swap! [this f x]
    (reset! this (f @state x)))
  (-swap! [this f x y]
    (reset! this (f @state x y)))
  (-swap! [this f x y xs]
    (reset! this (apply f @state x y xs))))

;; FIXME Ugly API because of backward compatibility with Freactive

(defn rx*
  ([getter] (rx* getter true nil))
  ([getter lazy?] (rx* getter lazy? nil))
  ([getter lazy? teardown & {:keys [meta validator setter]}]
   (RLens. (volatile! ::none)                               ; state
           getter lazy? teardown setter meta validator
           (volatile! {})                                   ; watches
           (volatile! #{})                                  ; sinks
           (volatile! #{})                                  ; sources
)))

(defn cursor [parent korks]
  (let [korks (if (coll? korks) korks [korks])]
    (rx* #(get-in @parent korks)
         false nil
         :setter #(swap! parent assoc-in korks %)
         :meta {::fingerprint [parent korks]})))