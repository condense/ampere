(ns freactive.core
  "# Ampere reactive mechanics

   *Sources* and *Sinks* are linked into the reactive computational graph.
   *Source* is responsible for establishing link with its *Sink* when *Sink* evaluates its expression
   and dereferences *Source* in it. On value change *Source* traverses every linked *Sink,*
   removes link with it (because *Sink's* expression may be dynamic and may not dereference current *Source* on the next pass),
   and invalidates it. In its turn *Sink* evaluates its expression on invalidate causing required *Sources* to establish links with it.

   Usually *Source* has an option to invalidate *Sinks* on any touch (eager) or only when value has been changed (lazy).
   By default `RAtom` is eager and `RLens` is lazy, but behaviour of either is controlled by the `lazy?` parameter.

   `RAtom` (factory function `atom`) behaves just like `cljs.core/Atom`, but represents *Source* capable of linking to *Sinks* and invalidating them on update.

   `RLens` (factory function `rx*`, macro `rx`) takes `getter` parameter which is evaluated on its dereference
   (actually, value is cached and `getter` is called only on the first dereference and on consecutive invalidations),
   acting as a *Sink* for any *Source* dereferenced in `getter`. Could be used as *Source* in other reactive expressions.
   Supports watches like atom.

   ### TODO:

   * research autodispose possibilities further;
   * move code under ampere ns, because nothing left from initial freactive.core."
  (:refer-clojure :exclude [atom]))

(def ^:dynamic *sink*
  "Current Source dereferencing context.
   Sink is responsible for binding it before computing its expression,
   Source is responsible for establishing link between itself and Sink being dereferenced in Sink's expression."
  nil)

(defprotocol IReactive "Marker protocol for sanity checks (like in `ampere.middleware/pure`)")

(defprotocol ISink
  (invalidate [_] "Recompute reactive expression")
  (add-source [_ source] "Track linked source")
  (remove-source [_ source] "Stop tracking source")
  (dispose [_] "Disconnect from sources if not used anymore (no sinks and watches left) to unlock self GCing"))

(defprotocol ISource
  (add-sink [_ sink] "Add sink to invalidate on change")
  (remove-sink [_ sink] "Stop invalidating sink on change")
  (invalidate-sinks [_] "Invalidate linked sinks"))

(defn add-link [source sink]
  (add-sink source sink)
  (add-source sink source))

(defn remove-link [source sink]
  (remove-sink source sink)
  (remove-source sink source))

(deftype RAtom [^:mutable state meta validator ^:mutable watches ^:mutable sinks lazy?]
  IReactive

  Object
  (equiv [this other] (-equiv this other))

  IEquiv
  (-equiv [o other] (identical? o other))

  IDeref
  (-deref [this]
    (when *sink* (add-link this *sink*))
    state)

  ISource
  (invalidate-sinks [this]
    (doseq [sink sinks]
      (remove-link this sink)
      (invalidate sink)))
  (add-sink [this sink]
    (set! sinks (conj sinks sink))
    this)
  (remove-sink [this sink]
    (set! sinks (disj sinks sink))
    this)

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
    this)

  IHash
  (-hash [this] (goog/getUid this))

  IPrintWithWriter
  (-pr-writer [this writer opts]
    (-write writer "#<RAtom: ")
    (pr-writer state writer opts)
    (-write writer ">"))

  IReset
  (-reset! [this new-value]
    (when-not (nil? validator)
      (assert (validator new-value) "Validator rejected reference state"))
    (let [old-value state]
      (when-not (and lazy? (= old-value new-value))
        (set! state new-value)
        (invalidate-sinks this)
        (-notify-watches this old-value new-value))
      new-value))

  ISwap
  (-swap! [this f]
    (reset! this (f state)))
  (-swap! [this f x]
    (reset! this (f state x)))
  (-swap! [this f x y]
    (reset! this (f state x y)))
  (-swap! [this f x y xs]
    (reset! this (apply f state x y xs))))

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
  ([x] (RAtom. x nil nil {} #{} false))
  ([x & {:keys [meta validator lazy?]}] (RAtom. x meta validator {} #{} lazy?)))

(deftype RLens [^:mutable state getter lazy? teardown setter meta validator ^:mutable watches ^:mutable sinks ^:mutable sources]
  IReactive

  Object
  (equiv [this other] (-equiv this other))

  IEquiv
  (-equiv [o other] (identical? o other))

  IDeref
  (-deref [this]
    (when *sink* (add-link this *sink*))
    (when (= state ::none) (invalidate this))
    state)

  ISink
  (invalidate [this]
    (let [old-value state
          new-value (binding [*sink* this] (getter))]
      (when-not (and lazy? (= old-value new-value))
        (set! state new-value)
        (invalidate-sinks this)
        (-notify-watches this old-value new-value))))
  (add-source [this source]
    (set! sources (conj sources source))
    this)
  (remove-source [this source]
    (set! sources (disj sources source))
    this)
  (dispose [this]
    (when (and (empty? sinks)
               (empty? watches))
      (doseq [source sources]
        (remove-sink source this)
        #_(when (satisfies? ISink source)
          (dispose source)))
      (when teardown (teardown this))))

  ISource
  (invalidate-sinks [this]
    (doseq [sink sinks]
      (remove-link this sink)
      (invalidate sink))
    ;; Try to dispose itself if all parent Sinks don't require this Source anymore
    (dispose this))
  (add-sink [this sink]
    (set! sinks (conj sinks sink))
    this)
  (remove-sink [this sink]
    (set! sinks (disj sinks sink))
    this)

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
  (-pr-writer [this writer opts]
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
   (RLens. ::none                                           ; state
           getter lazy? teardown setter meta validator
           {}                                               ; watches
           #{}                                              ; sinks
           #{}                                              ; sources
)))

(defn cursor* [parent korks]
  (let [korks (if (coll? korks) korks [korks])]
    (rx* #(get-in @parent korks)
         false nil
         :setter #(swap! parent assoc-in korks %))))

(def cursor (memoize cursor*))