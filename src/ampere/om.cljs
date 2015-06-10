(ns ampere.om
  "Om-specific API"
  (:require-macros [freactive.macros :refer [rx]])
  (:require [om.core :as om :include-macros true]
            [freactive.core :refer [dispose]]
            [ampere.core :refer [subscribe]]
            [ampere.router :as router]
            [ampere.utils :as utils]))

(defn- sub [owner subs]
  ;; NOTE do not put subscribe inside rx!
  (let [subs (utils/map-vals subscribe subs)
        subs (rx (utils/map-vals deref subs))]
    (.addInvalidationWatch subs :om #(om/refresh! owner))
    (om/set-state! owner :subs subs)))

(defn- unsub [owner]
  (let [subs (om/get-state owner :subs)]
    (.removeInvalidationWatch subs :om)
    (dispose subs)))

(defn- Wrapper
  "Wrapper component that tracks reactions and rerender `f` wrappee on their run
   with their values merged into cursor.
   E. g. `{:opts {:subs {:x [:sub-id1 params] :y [:sub-id2 params}}}`
   will inject `{:x @x-reaction :y @y-reaction}` into `f` props."
  [[f cursor m subs] owner]
  (reify
    om/IDisplayName
    (display-name [_] "Ampere Om Wrapper")
    om/IWillMount
    (will-mount [_] (sub owner subs))
    om/IWillReceiveProps
    (will-receive-props [_ [_ _ _ next-subs]]
      (when (not= next-subs (om/get-props owner 3))
        (unsub owner)
        (sub owner next-subs)))
    om/IWillUnmount
    (will-unmount [_] (unsub owner))
    om/IRenderState
    (render-state [_ {:keys [subs]}]
      (om/build* f (merge cursor @subs) m))))

(defn instrument
  "Add this as `:instrument` in `om/root` options to enable components having
  `:subs` in their `:opts` to subscribe to derived data & merge it into props.
  It uses `ampere/subscribe` in more om-ish way."
  [f cursor m]
  (if-let [subs (get-in m [:opts :subs])]
    (om/build* Wrapper [f cursor (update m :opts dissoc :subs) subs])
    ::om/pass))

(defn init! []
  (set! router/*flush-dom* om/render-all))