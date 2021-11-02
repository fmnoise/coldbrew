(ns fmnoise.coldbrew
  (:import (com.github.benmanes.caffeine.cache CacheLoader LoadingCache Caffeine)
           (java.time Duration)))

(defn cached
  "Accepts a function and creates cached version of it which uses Caffeine Loading Cache.
  Cache expiration and refresh time (in seconds) can be provided as `:expire` and `:refresh` meta to function.
  Example:
  ```
  (cached ^{:expire 86400 :refresh 36000}
   (fn [db id] (query db id)))
  ```"
  [f]
  (let [cache-builder (Caffeine/newBuilder)
        _ (when-let [expire (-> f meta :expire)]
            (.expireAfterWrite cache-builder (Duration/ofSeconds expire)))
        _ (when-let [refresh (-> f meta :refresh)]
            (.refreshAfterWrite cache-builder (Duration/ofSeconds refresh)))
        cache (.build cache-builder (reify CacheLoader (load [_ key] (apply f key))))]
    (fn [& args] (.get ^LoadingCache cache args))))

(defmacro defcached
  "Creates a function which uses Caffeine Loading Cache under the hood.
  Function declaration is similar to defn:
  - name (symbol) with optional meta
  - args (vector)
  - caching key (vector) with optional meta containing cache options `:expire` and `:refresh` for cache expiration and refresh time (in seconds)
  - body
  Example:
  ```
  (defcached customer-lifetime-value [db date {:customer/keys [id]}]
   ^{:expire 86400 :refresh 36000}
   [db date id]
   (query-customer-ltv db date id))
  ```"
  [name args cache-key & fn-body]
  (let [fname (gensym (str name "-cached-"))]
    `(do
      (def ^:private ~fname
         (cached
          (with-meta
            (fn [~@cache-key]
              ~@fn-body)
            ~(meta cache-key))))
      (defn ~name ~args (~fname ~@cache-key))
      (vary-meta ~name merge ~(meta name)))))
