(ns freactive.core
  (:refer-clojure :exclude [atom])
  (:require [clojure.set :as s]))

(def conj* (fnil conj #{}))

(defprotocol IReactiveSource
  (get-rank [_])
  (add-sink [_ sink])
  (remove-sink [_ sink])
  (get-sinks [_]))

(defprotocol IReactiveExpression
  (compute [_])
  (uncompute [_])
  (add-source [_ source])
  (remove-source [_ source]))

(def ^:dynamic *rx* nil)                                    ; current parent expression
(def ^:dynamic *rank* nil)                                  ; highest rank met during expression compute
(def ^:dynamic *queue* nil)                                 ; dirty sources and sinks

(declare propagate!)

(defn watch [_ source o n]
  (when (not= o n)
    (if *queue*
      (vswap! *queue* update (get-rank source) conj* source)
      (propagate! (volatile! {(get-rank source) #{source}})))))

(defn register [source]
  (when *rx*                                                ; *rank* too
    (add-watch source ::rx watch)
    (add-sink source *rx*)
    (add-source *rx* source)
    (vswap! *rank* max (get-rank source))))

(defn level-up! [queue rank]
  (reduce
    (fn [q sink]
      (update q (get-rank sink) conj* sink))
    (dissoc queue rank)
    (apply s/union (map get-sinks (get queue rank)))))

(defn propagate! [queue]
  (loop [rank 0 processed '()]
    (vswap! queue level-up! rank)
    (if-let [rank (apply min (keys @queue))]
      (do (binding [*queue* queue]
            (doseq [sink (get @queue rank)]
              (compute sink)
              (conj processed sink)))
          (recur rank processed))
      (doseq [sink processed]
        (uncompute sink)))))

(defn dosync* [f]
  (let [queue (or *queue* (volatile! {}))]
    (binding [*queue* queue] (f))
    (propagate! queue)))

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
          r (volatile! -1)
          new-value (binding [*rx* this
                              *rank* r]
                      (getter))]
      (set! rank (inc @r))
      (when (not= old-value new-value)
        (set! state new-value)
        (-notify-watches this old-value new-value))))
  (uncompute [this]
    (when (and (empty? sinks)
               (empty? (dissoc watches ::rx)))
      (doseq [source sources]
        (remove-sink source this)
        (when (satisfies? IReactiveExpression source)
          (uncompute source)))
      (set! sources #{})
      (set! state ::thunk)
      (when teardown (teardown))))
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
    (uncompute this)
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
    (reset! this (f state)))
  (-swap! [this f x]
    (reset! this (f state x)))
  (-swap! [this f x y]
    (reset! this (f state x y)))
  (-swap! [this f x y xs]
    (reset! this (apply f state x y xs))))

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