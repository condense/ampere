(ns ampere.om
  "Om-specific API"
  (:require [om.core :as om :include-macros true]
            [ampere.core :refer [subscribe]]
            [ampere.db :refer [app-db]]
            [ampere.router :as router]
            [ampere.utils :as utils]))

(defn get-key
  "Identify subscription by its app-db binding, name and parameters."
  [v]
  [app-db v])

(defn get-id
  "Identify component for subscription watchers."
  [c]
  (or (om/get-state c ::id)
      (let [id (gensym)]
        (om/set-state-nr! c ::id id)
        id)))

(defn sub
  "Refresh component `c` on subscription `v` update."
  [c v]
  (let [rx (subscribe v)]
    (om/set-state-nr! c [::rx (get-key v)] rx)
    (add-watch rx (get-id c) #(om/refresh! c))
    rx))

(defn unsub [c rx]
  (remove-watch rx (get-id c)))

(defn unsub-all [c]
  (doseq [rx (vals (om/get-state c ::rx))]
    (unsub c rx))
  (om/set-state-nr! c ::rx {}))

(defn observe
  "Used by Wrapper to subscribe to [:opts :subs] and could be directly called by component in render.

  FIXME Unobserve subscriptions made by direct call in render but not used in subsequent renders."
  [c v]
  @(or (om/get-state c [::rx (get-key v)]) (sub c v)))

(defn lifecycle-bind-app-db [f]
  (fn [& args]
    (this-as this
      (binding [app-db (or (om/get-state this ::db) app-db)]
        (.apply f this (into-array args))))))

(defn wrap-lifecycle [methods k]
  (update methods k lifecycle-bind-app-db))

(defn wrap-lifecycles [methods ks]
  (reduce wrap-lifecycle methods ks))

;; FIXME add app-db binding for all lifecycle methods
(def descriptor
  (om/specify-state-methods!
   (clj->js
    (-> om/pure-methods
        (update :render
                (fn [f]
                  (fn [& args]
                    (this-as this
                      ;(unsub-all this)
                      (.apply f this (into-array args))))))
        (update :componentWillUnmount
                (fn [f]
                  (fn []
                    (this-as this
                      (unsub-all this)
                      (.call f this)))))
        (wrap-lifecycles [:shouldComponentUpdate
                          :componentWillMount :componentDidMount
                          :componentWillUpdate :componentDidUpdate
                          :componentWillReceiveProps :render])))))

(def mergeable? (some-fn nil? map?))

(defn- Wrapper
  "Wrapper component that tracks subscriptions and rerender `f` wrappee on their update with their values merged into cursor.
   E. g. `{:opts {:subs {:x [:sub-id1 params] :y [:sub-id2 params}}}` will inject `{:x @x-subscription :y @y-subscription}` into `f` props."
  [props owner]
  (reify
    om/IDisplayName
    (display-name [_] "Ampere Om Wrapper")
    om/IRender
    (render [_]
      (unsub-all owner)
      (let [{:keys [f cursor m subs]} props
            rx (cond (vector? subs) (observe owner subs)
                     (map? subs) (utils/map-vals (partial observe owner) subs)
                     (nil? subs) nil
                     :else (do (utils/error "[:opts :subs] is expected to be either vector or map or nil, but got " subs) nil))]
        (when-not (or (nil? rx) (mergeable? cursor))
          (utils/error "cursor is expected to be either nil or map to be merged with subscriptions, but got " cursor))
        (om/build* f
                   (if (mergeable? cursor) (merge cursor rx) cursor)
                   (-> m
                       (assoc :descriptor descriptor)
                       (assoc-in [:state ::db] app-db)))))))

(defn instrument
  "Set this fn as `:instrument` in `om/root` options to enable Ampere subscriptions for Om.

  There are two ways to observe subscriptions by Om component:

  * provide `:subs` map under the `:opts` of `om/build` third argument; map must be in the format `{:key subscription-vector ...}`, and subscription value will be merged into component props under the `:key` on render;
  * call `(ampere.om/observe owner subscription-vector)` inside render to get subscription value (and component's refresh on its change)."
  [f cursor m]
  (om/build* Wrapper
             {:f      f
              :cursor cursor
              :m      m
              :db     app-db
              :subs   (get-in m [:opts :subs])}
             {:descriptor descriptor
              :state      {::db app-db}}))

(defn init! []
  (set! router/*flush-dom* om/render-all))