# Ampere

![Amperemeter](http://upload.wikimedia.org/wikipedia/commons/thumb/3/3d/Amperemeter_hg.jpg/200px-Amperemeter_hg.jpg)

View-agnostic fork of [re-frame][1]. Adapters for [Om][2] and [Reagent][3] are included.
For subscriptions uses [freactive.core][4] `rx` which provides very similar functionality to Reagent's `reaction`,
but allows more control, which helped to build non-leaky adapter for Om (it turned being tricky with `reaction`).

## Why?

Because I like re-frame approach, but sometimes I need to use Om in projects, but its cursors acts like MegaOhm resistors in architecture. We need more electric current to light up the bulb!
And... if Om, why not any another View library? You can use Ampere with Om, Reagent — or write adapter for your favorite lib, it is easy to do.

## Usage

```
[condense/ampere "0.2.1"]
```

See [re-frame's README][1] for general architecture use, [freactive.core's README][4] for deriving data with its tools.
[TodoMVC][5] example is included in this repo.

## Licence

Copyright © 2015 Ruslan Prokopchuk

Based on [re-frame][1], copyright © 2015 Michael Thompson

Distributed under The MIT License (MIT) - See LICENSE.txt

[1]: https://github.com/Day8/re-frame
[2]: https://github.com/omcljs/om
[3]: https://github.com/reagent-project/reagent
[4]: https://github.com/aaronc/freactive.core
[5]: https://github.com/condense/ampere/tree/master/examples/todomvc/
