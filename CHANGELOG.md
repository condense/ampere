# v.0.2.0-SNAPSHOT / 2015-06-10

* **BREAKING:** now subscriptions are based on freactive's `rx`
* **BREAKING:** to use Reagent you must to init its adapter with `ampere.reagent/init!` and use `ampere.reagent/subscribe` instead of `ampere.core/subscribe`
* **FIX:** Om adapter based on watching `reaction` forced component to refresh in an infinite loop