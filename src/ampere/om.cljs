(ns ampere.om
  "Om-specific API"
  (:require-macros [freactive.macros :refer [rx]])
  (:require [om.core :as om :include-macros true]
            [freactive.core :as r :refer [dispose]]
            [ampere.core :refer [subscribe]]
            [ampere.db :refer [app-db]]
            [ampere.router :as router]
            [ampere.utils :as utils]))

(defn adapt-state
  "Wrapper for freactive.core atom for use in `om/root` call.
  Default Om implementation of root cursor protocols is built on presumption that atom watchers
  are triggered on any atom update, even if its value didn't change. But freactive.core atoms don't
  trigger watchers if content remain unchanged after update.
  Thus wrapper is needed to make rerendering reliable."
  [state]
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

(defn unsub* [c rx]
  (remove-watch rx (get-id c))
  (dispose rx))

(defn unsub
  "Stop watching subscription `v` and try to GC its instance (if no other watchers left)."
  [c v]
  (let [k (get-key v)]
    (when-let [rx (om/get-state c [::rx k])]
      (om/update-state-nr! c ::rx #(dissoc % k))
      (unsub* c rx))))

(defn observe
  "Used by Wrapper to subscribe to [:opts :subs] and could be directly called by component in render.

  FIXME Unobserve subscriptions made by direct call in render but not used in subsequent renders."
  [c v]
  @(or (om/get-state c [::rx (get-key v)]) (sub c v)))

;; FIXME add app-db binding for all lifecycle methods
(def descriptor
  (om/specify-state-methods!
   (clj->js
    (-> om/pure-methods
        (update :componentWillUnmount
                (fn [f]
                  (fn []
                    (this-as this
                             (doseq [rx (vals (om/get-state this ::rx))]
                               (unsub* this rx))
                             (.call f this)))))
        (update :render
                (fn [f]
                  (fn []
                    (this-as this
                             (binding [app-db (or (om/get-state this ::db) app-db)]
                               (.call f this))))))))))

(def mergeable? (some-fn nil? map?))

(defn- Wrapper
  "Wrapper component that tracks subscriptions and rerender `f` wrappee on their update with their values merged into cursor.
   E. g. `{:opts {:subs {:x [:sub-id1 params] :y [:sub-id2 params}}}` will inject `{:x @x-subscription :y @y-subscription}` into `f` props."
  [props owner]
  (reify
    om/IDisplayName
    (display-name [_] "Ampere Om Wrapper")
    om/IWillReceiveProps
    (will-receive-props [_ next-props]
      (let [{:keys [db subs]} (om/get-props owner)
            next-subs (:subs next-props)]
        (binding [app-db db]
          (cond
            (not= db (:db next-props))
            (doseq [v subs] (unsub owner v))

            (not= subs next-subs)
            (cond
              (vector? subs) (unsub owner subs)
              (vector? next-subs) (doseq [v subs] (unsub owner v))
              :else
              (let [s1 (-> subs vals set)
                    s2 (-> next-subs vals set)
                    garbage (clojure.set/difference s1 s2)]
                (doseq [v garbage]
                  (unsub owner v))))

            :else nil))))
    om/IRender
    (render [_]
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