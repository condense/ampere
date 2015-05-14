# Simple Example of using Ampere with Om and VFSM

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
   cd examples/simple
   ```

1. clean build
   ```
   lein do clean, cljsbuild once
   ```

1. run
   ```
   open resources/public/index.html
   ```

## Exploring The Code

Please see [TodoMVC](https://github.com/ul/ampere/tree/master/examples/todomvc)
example for more detailed walkthrough of Ampere basics.

Interesting part of that example is using VFSM for implementing handler control logic.
Open `resources/example.graphml` with [yEd](http://www.yworks.com/en/products/yfiles/yed/)
and enjoy how simply is logic defined, and how easy to spot flaws in logic on picture:
ERROR node has no transitions from it, machine got stuck on error.

See [VFSM](https://github.com/ul/vfsm) for details about how to draw those fancy machines.