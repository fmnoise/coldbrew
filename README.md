### [🇺🇦 If you would like to support my open source work, please consider helping my country to defend freedom and right to exist 🇺🇦](https://stand-with-ukraine.pp.ua/)

# coldbrew [![Clojars Project](https://img.shields.io/clojars/v/org.clojars.fmnoise/coldbrew.svg)](https://clojars.org/org.clojars.fmnoise/coldbrew)

Easy to use Clojure wrappers for [Caffeine](https://github.com/ben-manes/caffeine)

<p align="center"><img src="https://user-images.githubusercontent.com/4033391/138520795-69732f8a-3790-4a2c-84e5-aa75fe7f626d.png" width="200"></p>

## Usage

```clojure
(require '[fmnoise.coldbrew :refer [cached defcached]])
```

The main building block is `cached` function which accepts function and returns cached version of it.
Cache options are passed as meta attached to function. Supported options are:
- `:expire` - expiration time (in seconds), uses [expireAfterWrite](https://github.com/ben-manes/caffeine/wiki/Eviction#time-based)
- `:refresh` - refresh time (in seconds), uses [refreshAfterWrite](https://github.com/ben-manes/caffeine/wiki/Refresh)
- `:when` - function for performing conditional caching (value is cached only if function returns truthy value)

Let's create a cached function with expiration time of 1 hour (3600 sec):
```clojure
(defn fetch-customer [base-url id]
  (http/get (str base-url "/customers/" id)))

(def cached-customer-fetch
  (cached (with-meta fetch-customer {:expire 3600})))
```

We can achieve the same result using an anonymous function:
```clojure
(def cached-customer-fetch
  (cached
    (with-meta
      (fn [base-url id]
        (http/get (str base-url "/customers/" id)))
      {:expire 3600})))
```

We can also configure that the value should be cached only when it has satisfied a condition:
```clojure
;; tasks can be only added to worker and capacity can only decrease
;; so it makes sense to cache value when capacity reaches 0
(def worker-tasks-capacity
  (cached
    (with-meta
      (fn [db worker-id date]
        (some-expensive-query db worker-id date))
      {:when zero?})))
```

There's also a more flexible `defcached` macro which uses `cached` under the hood.
The main purpose of it is ability to build cache keys from function agruments:

```clojure
(defcached fetch-customer ;; function name
  [{:keys [base-url timeout]} id] ;; function args
  ^{:expire 3600} ;; cache options passed as meta attached to cache key
  [id] ;; cache key - a vector which will become args for internal caching function
  ;; function body
  (let [result (deref (http/get (str base-url "/customer/" id)) timeout ::timeout)]
    (if (= result ::timeout)
      (throw (ex-info "Request timed out" {:id id}))
      result))
```

All meta passed to function name is preserved, so we can have private cached functions and add docstrings:
```clojure
(defcached
  ^{:private true
    :doc "Fetches customer with given id"}
  fetch-customer-impl [{:keys [base-url timeout]} id]
  ^{:expire 3600}
  [id]
  (let [result (deref (http/get (str base-url "/customer/" id)) timeout ::timeout)]
    (if (= result ::timeout)
      (throw (ex-info "Request timed out" {:id id}))
      result)))
```

Positional docstring as well as `pre`/`post` conditions map are also supported:
```clojure
(defcached fetch-customer-impl
  "Fetches customer data by given id"
  [{:keys [base-url timeout]} id]
  {:pre [(string? base-url) (pos? timeout) (some? id)]}
  ^{:expire 3600}
  [id]
  (let [result (deref (http/get (str base-url "/customer/" id)) timeout ::timeout)]
    (if (= result ::timeout)
      (throw (ex-info "Request timed out" {:id id}))
      result)))
```

Due to defn-like declaration it's very easy to refactor existing `defn` to cached function using the `defcached` macro:
1. Change `defn` to `defcached`
2. Add cache key vector before function body (names should correspond with function args) with optional metadata for expiration/refreshing
3. That's it! :tada:

*NB: If you later decide to return back to `defn` and forget to remove cache key, nothing will break.*

## Disclaimer

Same as consuming [cold brew coffee](https://en.wikipedia.org/wiki/List_of_coffee_drinks#Cold_brew), make sure you don't exceed recommended caffeine amount, as each call to `cached` creates separate Caffeine Loading Cache instance. Also make sure you understand the risk of memory leaks when caching large objects or collections.

## Status

Successful production usage 😎

## Credits

Coldbrew icon made by [Freepik](https://www.freepik.com) from [Flaticon](https://www.flaticon.com)

## License

Copyright © 2021 fmnoise

Distributed under the [Eclipse Public License 2.0](http://www.eclipse.org/legal/epl-2.0)
