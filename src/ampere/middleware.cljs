(ns ampere.middleware
  (:require
   [freactive.core :refer [IReactiveSource]]
   [ampere.undo :refer [store-now!]]
   [ampere.utils :refer [warn log group groupEnd error]]
   [clojure.data :as data]))

;;; See docs in the [Wiki](https://github.com/Day8/re-frame/wiki)

(defn pure
  "Acts as an adaptor, allowing handlers to be writen as pure functions.
  The ampere router passes the `app-db` atom as the first parameter to any handler.
  This middleware adapts that atom to be the value within the atom.
  If you strip away the error/efficiency checks, this middleware is doing:
  `(reset! app-db (handler @app-db event-vec))`
  You don't have to use this middleware directly. It is automatically applied to
  your handler's middleware when you use `register-handler`.
  In fact, the only way to by-pass automatic use of `pure` in your middleware
  is to use the low level registration function `ampere.handlers/register-handler-base`"
  [handler]
  (fn pure-handler
    [app-db event-vec]
    (if (satisfies? IReactiveSource app-db)
      (let [db @app-db
            new-db (handler db event-vec)]
        (if (nil? new-db)
          (error "ampere: your pure handler returned nil. It should return the new db state.")
          (when-not (identical? db new-db)
            (reset! app-db new-db))))                       ; turn this into a noop handler
      (do
        (if (map? app-db)
          (warn "ampere: Looks like \"pure\" is in the middleware pipeline twice. Ignoring.")
          (warn "ampere: \"pure\" middleware not given an IReactiveSource.  Got: " app-db))
        handler))))

(defn log-ex
  "Middleware which catches and prints any handler-generated exceptions to console.
  Handlers are called from within a core.async go-loop, and core.async produces
  a special kind of hell when in comes to stacktraces. By the time an exception
  has passed through a go-loop its stack is mangled beyond repair and you'll
  have no idea where the exception was thrown.
  So this middleware catches and prints to stacktrace before the core.async sausage
  machine has done its work."
  [handler]
  (fn log-ex-handler
    [db v]
    (try
      (handler db v)
      (catch :default e                                   ; ooops, handler threw
        (do
          (.error js/console (.-stack e))
          (throw e))))))

(defn debug
  "Middleware which logs debug information to js/console for each event.
  Includes a clojure.data/diff of the db, before vs after, showing the changes
  caused by the event."
  [handler]
  (fn debug-handler
    [db v]
    (group "ampere event: " (first v))
    (let [new-db (handler db v)
          diff (data/diff db new-db)]
      (log "event:" v)
      (log "only before: " (first diff))
      (log "only after : " (second diff))
      (groupEnd)
      new-db)))

(defn trim-v
  "Middleware which removes the first element of v, allowing you to write
  more aesthetically pleasing handlers. No leading underscore on the event-v!
  Your handlers will look like this:

      (defn my-handler
        [db [x y z]]    ;; <-- instead of [_ x y z]
        ....)
  "
  [handler]
  (fn trim-v-handler
    [db v]
    (handler db (vec (rest v)))))

(defn path
  "A middleware factory which supplies a sub-tree of `db` to the handler.
  Works a bit like update-in. Supplies a narrowed data structure for the handler.
  Afterwards, grafts the result of the handler back into db.
  Usage:

      (path :some :path)
      (path [:some :path])
      (path [:some :path] :to :here)
      (path [:some :path] [:to] :here)
  "
  [& args]
  (let [path (flatten args)
        _ (if (empty? path)
            (error "ampere: \"path\" middleware given no params."))
        _ (if (fn? (first args))
            (error "ampere: you've used \"path\" incorrectly. It is a middleare factory and must be called like this \"(path something)\", whereas you just supplied \"path\"."))]
    (fn path-middleware
      [handler]
      (fn path-handler
        [db v]
        (assoc-in db path (handler (get-in db path) v))))))

(defn undoable
  "A Middleware factory which stores an undo checkpoint.
  `explanation` can be either a string or a function. If it is a
  function then must be:  (db event-vec) -> string.
  `explanation` can be nil. in which case \"\" is recorded."
  [explanation]
  (fn undoable-middleware
    [handler]
    (fn undoable-handler
      [db event-vec]
      (let [explanation (cond
                          (fn? explanation) (explanation db event-vec)
                          (string? explanation) explanation
                          (nil? explanation) ""
                          :else (error "ampere: \"undoable\" middleware given a bad parameter. Got: " explanation))]
        (store-now! explanation)
        (handler db event-vec)))))

(defn enrich
  "Middleware factory which runs a given function `f` in the after position.
  `f` is (db v) -> db
  Unlike `after` which is about side effects, `enrich` expects f to process and alter
  db in some useful way, contributing to the derived data, flowing vibe.
  Imagine that todomvc needed to do duplicate detection - if any two todos had
  the same text, then highlight their background, and report them in a warning
  down the bottom.
  Almost any action (edit text, add new todo, remove a todo) requires a
  complete reassesment of duplication errors and warnings. Eg: that edit
  update might have introduced a new duplicate or removed one. Same with a
  todo removal.
  And to perform this enrichment, a function has to inspect all the todos,
  possibly set flags on each, and set some overall list of duplicates.
  And this duplication check might just be one check amoung many.
  `f` would need to be both adding and removing the duplicate warnings.
  By applying `f` in middleware, we keep the handlers simple and yet we
  ensure this important step is not missed."
  [f]
  (fn enrich-middleware
    [handler]
    (fn enrich-handler
      [db v]
      (f (handler db v) v))))

(defn after
  "Middleware factory which runs a function `f` in the \"after handler\"
  position presumably for side effects.
  `f` is given the new value of `db`. It's return value is ignored.
  Examples: `f` can run schema validation. Or write current state to localstorage. etc.
  In effect, `f` is meant to sideeffect. It gets no chance to change db. See \"enrich\"
  (if you need that.)"
  [f]
  (fn after-middleware
    [handler]
    (fn after-handler
      [db v]
      (let [new-db (handler db v)]
        (f new-db v)                                   ; call f for side effects
        new-db))))