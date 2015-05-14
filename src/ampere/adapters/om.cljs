(ns ampere.adapters.om
  "Om-specific API"
  (:require [om.core :as om :include-macros true]
            [ampere.router :as router]
            [ampere.utils :as utils]))

(defn- Wrapper
  "Wrapper component that tracks cells and rerender `f` wrappee on their change
   with their values merged into cursor.
   E. g. `{:opts {:cells {:x x-cell :y y-cell}}}`
   will inject `{:x @x-cell :y @y-cell}` into `f` props."
  [[f cursor m] owner]
  (reify
    om/IInitState
    (init-state [_]
      (assoc (utils/map-vals deref (get-in m [:opts :cells])) ::id (gensym)))
    om/IWillMount
    (will-mount [_]
      (let [id (om/get-state owner ::id)
            f (fn [k _ _ _ v] (om/set-state! owner k v))]
        (doseq [[k v] (get-in m [:opts :cells])]
          (add-watch v id (partial f k)))))
    om/IWillUnmount
    (will-unmount [_]
      (let [id (om/get-state owner ::id)]
        (doseq [[_ v] (get-in m [:opts :cells])]
          (remove-watch v id))))
    om/IRenderState
    (render-state [_ state]
      (om/build* f (merge cursor state) m))))

(defn instrument
  "Add this as `:instrument` in `om/root` options to enable components having
  `:cells` in their `:opts` to observe & merge those cells into props.
  It resembles `re-frame/subscribe`"
  [f cursor m]
  (if (get-in m [:opts :cells])
    (om/build* Wrapper [f cursor m])
    (om/build* f cursor m)))

(defn init! []
  (set! router/*flush-dom* om/render-all))