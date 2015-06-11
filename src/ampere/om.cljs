(ns ampere.om
  "Om-specific API"
  (:require-macros [freactive.macros :refer [rx]])
  (:require [om.core :as om :include-macros true]
            [freactive.core :as r :refer [dispose]]
            [ampere.core :refer [subscribe]]
            [ampere.router :as router]
            [ampere.utils :as utils]))

(defn get-key [v] (get (meta v) :key v))

(defn sub [c v]
  (let [rx (subscribe v)]
    (aset rx "__ampere_v" v)
    (om/set-state-nr! c [::rx (get-key v)] [v rx])
    (.addInvalidationWatch rx :om #(om/refresh! c))
    rx))

(defn unsub [c v]
  (let [k (get-key v)]
    (when-let [[_ rx] (om/get-state c [::rx k])]
      (.removeInvalidationWatch rx :om)
      (dispose rx)
      (om/update-state-nr! c ::rx #(dissoc % k)))))

(extend-protocol om/ICursor
  r/Cursor
  (-path [_])
  (-state [rx] rx)
  r/ReactiveExpression
  (-path [_])
  (-state [rx] rx))

(extend-protocol om/IValue
  r/Cursor
  (-value [rx] @rx)
  r/ReactiveExpression
  (-value [rx] @rx))

(extend-protocol om/IOmRef
  r/Cursor
  (-add-dep! [rx _] rx)
  (-remove-dep! [rx c] (unsub c (aget rx "__ampere_v")))
  (-refresh-deps! [_])
  (-get-deps [_])
  r/ReactiveExpression
  (-add-dep! [rx _] rx)
  (-remove-dep! [rx c] (unsub c (aget rx "__ampere_v")))
  (-refresh-deps! [_])
  (-get-deps [_]))

(defn upsert-ref [c v]
  (if-let [[prev-v rx] (om/get-state c [::rx (get-key v)])]
    (do
      (if (= prev-v v)
        rx
        ;; if you are passing variable in time args to subscription,
        ;; name it with static key to help gc:
        ;; (observe ^{:key :data1} [:data x y z])
        (do
          (.removeInvalidationWatch rx :om)
          (dispose rx)
          (sub c v))))
    (sub c v)))

(defn observe [c v]
  @(om/observe c (upsert-ref c v)))

(defn- Wrapper
  "Wrapper component that tracks reactions and rerender `f` wrappee on their run
   with their values merged into cursor.
   E. g. `{:opts {:subs {:x [:sub-id1 params] :y [:sub-id2 params}}}`
   will inject `{:x @x-reaction :y @y-reaction}` into `f` props."
  [[f cursor m subs] owner]
  (reify
    om/IDisplayName
    (display-name [_] "Ampere Om Wrapper")
    om/IWillReceiveProps
    (will-receive-props [_ [_ _ _ next-subs]]
      (let [subs (om/get-props owner 3)]
        (when (not= subs next-subs)
          (let [s1 (-> subs vals set)
                s2 (-> next-subs vals set)
                garbage (clojure.set/difference s1 s2)]
            (doseq [v garbage]
              (unsub owner v))))))
    om/IRender
    (render [_]
      (let [rx (utils/map-vals (partial observe owner) subs)]
        (om/build* f (merge cursor rx) m)))))

(defn instrument
  "Add this as `:instrument` in `om/root` options to enable components having
  `:subs` in their `:opts` to subscribe to derived data & merge it into props
  or calling `(ampere.om/observe owner ^{:key optional-key-if-sub-is-dynamic} subscription-vector)`
  inside render to track subscription.
  It uses `ampere/subscribe` in more om-ish way."
  [f cursor m]
  (if-let [subs (get-in m [:opts :subs])]
    (om/build* Wrapper [f cursor (update m :opts dissoc :subs) subs])
    ::om/pass))

(defn init! []
  (set! router/*flush-dom* om/render-all))