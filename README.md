# coldbrew [![Clojars Project](https://img.shields.io/clojars/v/org.clojars.fmnoise/coldbrew.svg)](https://clojars.org/org.clojars.fmnoise/coldbrew)

Clojure caching tools built on [Caffeine](https://github.com/ben-manes/caffeine)


## Usage

```clojure
(require '[fmnoise.coldbrew :refer [defcached cached-fn]])
```

Let's create a cached function with cache expiration time 1 hour(3600 sec).
Cache options are passed as meta to cached function. Supported options are:
- `:expire`, uses [expireAfterWrite](https://github.com/ben-manes/caffeine/wiki/Eviction#time-based)
- `:refresh`, uses [refreshAfterWrite](https://github.com/ben-manes/caffeine/wiki/Refresh)

We can use existing function:
```clojure
(defn fetch-customer [base-url id]
  (http/get (str base-url "/customers/" id)))

(def cached-customer-fetch
  (cached-fn (with-meta fetch-customer {:expire 3600})))
```

or anonymous function:
```clojure
(def cached-customer-fetch
  (cached-fn
    (with-meta
     (fn [base-url id]
       (http/get (str base-url "/customers/" id))
     {:expire 3600})))
```

There's also a macro to define cached function:
```clojure
(defcached fetch-customer ;; function name
  [base-url id] ;; function args
  ^{:expire 3600} ;; cache options passed as meta to cache key
  [base-url id] ;; cache key - a vector which will become args for internal caching function
  (http/get (str base-url "/customer/" id)) ;; function body
```

All meta passed to function name is preserved, so you can have private cached function and add docstring.

*NB: Docstring is only supported as meta to function var*

```clojure
(defcached
  ^{:private true
    :doc "Fetches customer with given id"}
  fetch-customer [base-url id]
  ^{:expire 3600}
  [base-url id]
  (http/get (str base-url "/customer/" id))
```

Due to defn-like declaration it's very easy to refactor existing `defn` to cached function using `defcached` macro:
1. Rename `defn` to `defcached`
2. Add cache key vector before body (names should correspond with function args) with optional meta for expiration/refreshing
3. That's it!

*NB: If you later decide to return back to `defn` and forget to remove cache key, nothing will break.*

## License

Copyright © 2021 fmnoise

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.