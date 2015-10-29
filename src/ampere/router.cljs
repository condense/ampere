(ns ampere.router
  (:require-macros [cljs.core.async.macros :refer [go-loop go]])
  (:require [ampere.handlers :refer [handle]]
            [ampere.db :refer [app-db]]
            [ampere.utils :refer [warn error]]
            [cljs.core.async :refer [chan put! <! timeout close! poll!]])
  (:import goog.async.nextTick))

(def ^:dynamic *flush-dom*
  "Renderer-specific function to flush DOM before CPU-intensive handler call.
  Must be set on app init by adapter."
  #(warn "ampere.router/*flush-dom* is not set, may be you forgot to init view adapter?"))

;;; ## The Event Conveyor Belt
;;;
;;; Moves events from "dispatch" to the router loop.
;;; Using core.async means we can have the aysnc handling of events.
(def ^:private event-chan (chan))                           ; TODO: set buffer size?

(defn purge-chan
  "Read all pending events from the channel and drop them on the floor."
  []
  (loop []
    (when (go (poll! event-chan))
      (recur))))

(defn yield
  "Yields control to the browser. Faster than (timeout 0).
  See http://dev.clojure.org/jira/browse/ASYNC-137"
  []
  (let [ch (chan)]
    (nextTick #(close! ch))
    ch))

(defn yield-chan
  "If `flush?`, then wait just over one annimation frame (16ms), to rensure all pending GUI work is flushed to the DOM.
   Else, just in case we are handling one dispatch after an other, give the browser back control to do its stuff,
   but only once per yield-time, to allow fast handlers to be processed in a batch and do not rerender too frequently."
  [flush? yield-time now]
  (cond flush? (do (flush) (timeout 20))
        (>= now yield-time) (yield)))

;;; ## Router loop

(defn router-loop
  "In a perpetual loop, read events from `event-chan`, and call the right handler.

   Because handlers occupy the CPU, before each event is handled, hand
   back control to the browser, via a `(<! (yield))` call.

   In some cases, we need to pause for an entire animationFrame, to ensure that
   the DOM is fully flushed, before then calling a handler known to hog the CPU
   for an extended period.  In such a case, the event should be labeled with metadata.

   Example usage (notice the `:flush-dom` metadata):

       (dispatch ^:flush-dom  [:event-id other params])
   "
  []
  (go-loop [yield-time 0]
    (let [[db event-v] (<! event-chan) ; wait for an event
          yield-time (if-let [ch (yield-chan (:flush-dom (meta event-v)) ; check the event for metadata
                                             yield-time
                                             (system-time))]
                       (do (<! ch)
                           (+ (system-time) 5))
                       yield-time)]
      (binding [app-db db]
        (try
          (handle event-v)

          ;; Unhandled exceptions from event handlers must be managed as follows:
          ;;   - call the standard logging function "error"
          ;;   - allow them to continue to bubble up because the app, in production,
          ;;     may have hooked window.onerror and perform special processing.
          ;;   - But an exception which bubbles out will break the enclosing go-loop.
          ;;     So we'll need to start another one.
          (catch js/Object e
            (do
              ;; try to recover from this (probably uncaught) error as best we can
              (purge-chan)                                  ; get rid of any pending events
              (router-loop)                                 ; Exception throw will cause termination of go-loop. So, start another.

              (throw e))))))                                ; re-throw so the rest of the app's infrastructure (window.onerror?) gets told
    (recur yield-time)))

;;; Start event processing.
(router-loop)

;;; ## Dispatch

;;; nil would close the channel
;;; Ensure nil return. See [here](https://github.com/Day8/re-frame/wiki/Beware-Returning-False)
(defn dispatch
  "Send an event to be processed by the registered handler.
  Usage example:
  `(dispatch [:delete-item 42])`"
  ([db event-v]
   (if (nil? event-v)
     (error "ampere: \"dispatch\" is ignoring a nil event.")
     (put! event-chan [db event-v]))
   nil)
  ([event-v] (dispatch app-db event-v)))

;;; Ensure nil return. See [here](https://github.com/Day8/re-frame/wiki/Beware-Returning-False)
(defn dispatch-sync
  "Send an event to be processed by the registered handler, but avoid the async-inducing
  use of core.async/chan.
  Usage example:
  `(dispatch-sync [:delete-item 42])`"
  ([event-v] (handle event-v) nil)
  ([db event-v] (binding [app-db db] (handle event-v))))
