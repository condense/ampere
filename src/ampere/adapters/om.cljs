(ns ampere.adapters.om
  "Om-specific API"
  (:require [om.core :as om :include-macros true]
            [ampere.router :as router]
            [ampere.utils :as utils]))

(defn- bind-cells [owner cells]
  (let [id (om/get-state owner ::id)
        f (fn [k _ _ _ v] (om/set-state! owner k v))]
    (doseq [[k v] cells]
      (add-watch v id (partial f k)))))

(defn- unbind-cells [owner cells]
  (let [id (om/get-state owner ::id)]
    (doseq [[_ v] cells]
      (remove-watch v id))))

(defn- Wrapper
  "Wrapper component that tracks cells and rerender `f` wrappee on their change
   with their values merged into cursor.
   E. g. `{:opts {:cells {:x x-cell :y y-cell}}}`
   will inject `{:x @x-cell :y @y-cell}` into `f` props."
  [[f cursor m cells] owner]
  (reify
    om/IInitState
    (init-state [_]
      (assoc (utils/map-vals deref cells) ::id (gensym)))
    om/IWillMount
    (will-mount [_]
      (bind-cells owner cells))
    om/IWillReceiveProps
    (will-receive-props [_ [_ _ _ next-cells]]
      (when (not= next-cells cells)
        (unbind-cells owner cells)
        (bind-cells owner next-cells)
        (let [id (om/get-state owner ::id)]
          (om/set-state!
           owner (assoc (utils/map-vals deref next-cells) ::id id)))))
    om/IWillUnmount
    (will-unmount [_]
      (unbind-cells owner cells))
    om/IRenderState
    (render-state [_ state]
      (om/build* f (merge cursor (dissoc state ::id)) m))))

(defn instrument
  "Add this as `:instrument` in `om/root` options to enable components having
  `:cells` in their `:opts` to observe & merge those cells into props.
  It resembles `re-frame/subscribe`"
  [f cursor m]
  (if-let [cells (get-in m [:opts :cells])]
    (om/build* Wrapper [f cursor (update m :opts dissoc :cells) cells])
    ::om/pass))

(defn init! []
  (set! router/*flush-dom* om/render-all))