# TodoMVC done with Ampere

An [Ampere](https://github.com/ul/ampere) implementation of [TodoMVC](http://todomvc.com/).


## Setup And Run

1. Install [Leiningen](http://leiningen.org/)  (plus Java).

1. Get the Ampere repo
   ```
   git clone https://github.com/ul/ampere.git
   cd ampere
   ```

1. Install it locally (not pushed in Clojars yet, sorry)
   ```
   lein install
   ```

1. cd to the right example directory
   ```
   cd examples/todomvc
   ```

1. clean build
   ```
   lein do clean, cljsbuild once
   ```

1. run
   ```
   open index.html
   ```

## Exploring The Code

```
To build an Ampere app, you:
  - design your app's data structure (data layer)
  - write formulæ cells (query layer)
  - write Om/Reagent/whatever-supported-by-adapters component functions (view layer)
  - write and register event handler functions (control layer and/or state transition layer)
  - complex logic could be drawn & compiled with VFSM and then registered with `vfsm` middleware
```

In `src`, there's a matching set of files (each small):
```
src
├── core.cljs         <--- entry point, plus history
├── db.cljs           <--- data related  (data layer)
├── subs.cljs         <--- formulæ cells (query layer)
├── views.cljs        <--- components (view layer)
└── handlers.cljs     <--- event handlers (control/update layer)
```

## Notes

Various:
 - The [offical reagent example](https://github.com/reagent-project/reagent/tree/master/examples/todomvc).
 - There's also a sibling example (under construction) called TodoMVC-plus which is a more complete example, including testing etc.
 - Look at the [re-frame Wiki](https://github.com/Day8/re-frame/wiki).
