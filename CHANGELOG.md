# Change Log

## Unreleased

* Add support for cache configuration:
  * scheduler
  * executor
  * eviction/removal listeners
  * initial capacity
* Add more caffeine function wrappers
  * get (.get, .getIfPresent)
  * invalidate
  * put
  * put-all (.putAll)

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
