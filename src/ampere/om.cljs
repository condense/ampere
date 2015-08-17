(ns ampere.om
  "Om-specific API"
  (:require-macros [freactive.macros :refer [rx]])
  (:require [om.core :as om :include-macros true]
            [freactive.core :as r :refer [dispose]]
            [goog.object :as obj]
            [ampere.core :refer [subscribe]]
            [ampere.router :as router]
            [ampere.utils :as utils]))

(defn get-key [v] (get (meta v) :key v))

(defn sub [c v]
  (let [rx (subscribe v)]
    (om/set-state-nr! c [::rx (get-key v)] [v rx])
    (let [id (or (om/get-state c ::id)
                 (let [id (gensym)]
                   (om/set-state-nr! c ::id id)
                   id))]
      (add-watch rx id #(om/refresh! c)))
    rx))

(defn unsub [c v]
  (let [k (get-key v)]
    (when-let [[_ rx] (om/get-state c [::rx k])]
      (remove-watch rx (om/get-state c ::id))
      (dispose rx)
      (om/update-state-nr! c ::rx #(dissoc % k)))))

(defn adapt-state [state]
  (let [properties (atom {})
        listeners (atom {})
        render-queue (atom #{})]
    (specify! state
              om/IRootProperties
              (-set-property! [_ id k v]
                              (swap! properties assoc-in [id k] v))
              (-remove-property! [_ id k]
                                 (swap! properties dissoc id k))
              (-remove-properties! [_ id]
                                   (swap! properties dissoc id))
              (-get-property [_ id k]
                             (get-in @properties [id k]))
              om/INotify
              (-listen! [this key tx-listen]
                        (when-not (nil? tx-listen)
                          (swap! listeners assoc key tx-listen))
                        this)
              (-unlisten! [this key]
                          (swap! listeners dissoc key)
                          this)
              (-notify! [this tx-data root-cursor]
                        (doseq [[_ f] @listeners]
                          (f tx-data root-cursor))
                        this)
              om/IRenderQueue
              (-get-queue [this] @render-queue)
              (-queue-render! [this c]
                              (when-not (contains? @render-queue c)
                                (swap! render-queue conj c)
                                (swap! this update ::c (fnil inc 0))))
              (-empty-queue! [this]
                             (swap! render-queue empty)))))

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
  (-remove-dep! [rx c] (unsub c (obj/get rx "__ampere_v")))
  (-refresh-deps! [_])
  (-get-deps [_])
  r/ReactiveExpression
  (-add-dep! [rx _] rx)
  (-remove-dep! [rx c] (unsub c (obj/get rx "__ampere_v")))
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
        (let [id (om/get-state c ::id)]
          (remove-watch rx id)
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