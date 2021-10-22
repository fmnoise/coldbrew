# coldbrew [![Clojars Project](https://img.shields.io/clojars/v/org.clojars.fmnoise/coldbrew.svg)](https://clojars.org/org.clojars.fmnoise/coldbrew)

Easy to use Clojure wrappers for [Caffeine](https://github.com/ben-manes/caffeine)

<p align="center"><img src="https://user-images.githubusercontent.com/4033391/138502127-52beb2a4-43c0-4650-9a96-27c4e9c47398.png" width="200"></p>

## Usage

```clojure
(require '[fmnoise.coldbrew :refer [cached defcached]])
```

The main building block is `cached` function which accepts function and returns cached version of it.
Cache options are passed as meta attached to function. Supported options are:
- `:expire`, uses [expireAfterWrite](https://github.com/ben-manes/caffeine/wiki/Eviction#time-based)
- `:refresh`, uses [refreshAfterWrite](https://github.com/ben-manes/caffeine/wiki/Refresh)

Let's create a cached function with expiration time 1 hour (3600 sec):
```clojure
(defn fetch-customer [base-url id]
  (http/get (str base-url "/customers/" id)))

(def cached-customer-fetch
  (cached (with-meta fetch-customer {:expire 3600})))
```

We can achieve same result using anonymous function:
```clojure
(def cached-customer-fetch
  (cached
    (with-meta
     (fn [base-url id]
       (http/get (str base-url "/customers/" id))
     {:expire 3600})))
```

There's also more flexible `defcached` macro which uses `cached` under the hood.
The main purpose of it is ability to build cache key from function agruments:
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

All meta passed to function name is preserved, so we can have private cached function and add docstring:
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

*NB: Docstring is only supported as meta*

Due to defn-like declaration it's very easy to refactor existing `defn` to cached function using `defcached` macro:
1. Change `defn` to `defcached`
2. Add cache key vector before function body (names should correspond with function args) with optional meta for expiration/refreshing
3. That's it!

*NB: If you later decide to return back to `defn` and forget to remove cache key, nothing will break.*

## Disclaimer

Same as consuming coldbrew drink in reality, make sure you don't exceed recommended coffeine amount, as each call to `cached` creates separate Caffeine Loading Cache instance. Also make sure you understand risk of memory leaks when caching large objects or collections.

## Credits

Coldbrew icon made by [Eucalyp](https://www.flaticon.com/authors/eucalyp)

## License

Copyright © 2021 fmnoise

Distributed under the [Eclipse Public License 2.0](http://www.eclipse.org/legal/epl-2.0)
