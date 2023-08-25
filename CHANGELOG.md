# Change Log

## 1.3.0

* **BREAKING!** `lookup` should accept 1-arity function
* Add `lookup-some` (`.getIfPresent`)
* `lookup` now uses `.get` for LoadingCache and `.getIfPresent` otherwise
* Add support for cache configuration:
  * scheduler
  * executor
  * eviction/removal listeners
  * initial capacity
* Add more caffeine function wrappers
  * `invalidate`
  * `put`
  * `put-all` (`.putAll`)

## 1.2.1 - 2023-05-22

* Additional arity for `lookup`

## 1.2.0 - 2023-05-22

* Fix building cache
* **BREAKING!**  remove `fetch`, introduce `lookup` and `cond-lookup`
* **BREAKING!** `build-cache` should accept function instead of loader

## 1.1.4 - 2023-05-22

* Use Clojure 1.9

## 1.1.3 - 2023-05-22

* Additional arities for `fetch`

## 1.1.2 - 2023-05-22

* Add `build-cache` and `fetch`

## 1.1.1 - 2023-02-16

* Remove clojure macro meta from options

## 1.1.0 - 2023-01-31

* Update defcached docstring
* Add more caching options and perform basic validation

## 1.0.0 - 2021-12-31

* Add positional docstring and pre/post conditions to defcached

## 0.2.3 - 2021-11-22

* Fix zero-arity functions caching

## 0.2.2 - 2021-11-10

* Conditional caching

## 0.2.1 - 2021-11-02

* Fix `defcached` implementation

## 0.2.0 - 2021-10-22

* **BREAKING!** `cached-fn` renamed to `cached`

## 0.1.0 - 2021-10-22

* First public release
