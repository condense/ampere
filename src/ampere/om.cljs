(ns ampere.om
  "Om-specific API"
  (:require [om.core :as om :include-macros true]
            [reagent.ratom :refer [dispose!] :refer-macros [reaction]]
            [ampere.core :refer [subscribe]]
            [ampere.router :as router]
            [ampere.utils :as utils]))

(defn- sub [owner subs]
  (let [subs (utils/map-vals subscribe subs)
        rx (reaction (utils/map-vals deref subs))]
    (add-watch rx :om #(om/refresh! owner))
    (om/set-state! owner :rx rx)))

(defn- unsub [owner]
  (dispose! (om/get-state owner :rx)))

(defn- Wrapper
  "Wrapper component that tracks reactions and rerender `f` wrappee on their run
   with their values merged into cursor.
   E. g. `{:opts {:subs {:x [:sub-id1 params] :y [:sub-id2 params}}}`
   will inject `{:x @x-reaction :y @y-reaction}` into `f` props."
  [[f cursor m subs] owner]
  (reify
    om/IWillMount
    (will-mount [_] (sub owner subs))
    om/IWillReceiveProps
    (will-receive-props [_ [_ _ _ next-subs]]
      (when (not= next-subs subs)
        (unsub owner)
        (sub owner next-subs)))
    om/IWillUnmount
    (will-unmount [_] (unsub owner))
    om/IRenderState
    (render-state [_ {:keys [rx]}]
      (om/build* f (merge cursor @rx) m))))

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