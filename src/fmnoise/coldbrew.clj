(ns fmnoise.coldbrew
  (:import (com.github.benmanes.caffeine.cache Cache CacheLoader LoadingCache Caffeine)
           (java.time Duration)))

(defn cached
  "Accepts a function and creates cached version of it which uses Caffeine Loading Cache.
  Cache options should can be provided as meta to function. Supported options are:
  `:expire` - expiration time (in seconds)
  `:refresh` - refresh time (in seconds)
  `:when` - function for checking if value should be cached

  Examples:
  (cached ^{:expire 86400 :refresh 36000}
   (fn [db id] (query db id)))

  ;; calculate todays order capacity until it reaches zero
  (cached ^{:when zero?}
   (fn [db worker-id] (order-capacity db worker-id)))
  "
  [f]
  (let [^Caffeine cache-builder (Caffeine/newBuilder)
        condition-fn (-> f meta :when)
        _ (when-let [expire (-> f meta :expire)]
            (.expireAfterWrite cache-builder (Duration/ofSeconds expire)))
        _ (when-let [refresh (-> f meta :refresh)]
            (.refreshAfterWrite cache-builder (Duration/ofSeconds refresh)))
        cache (if condition-fn
                (.build cache-builder)
                (.build cache-builder (reify CacheLoader (load [_ key] (apply f key)))))]
    (if condition-fn
      (fn [& args]
        (let [key (or args [])]
          (or (.getIfPresent ^Cache cache key)
              (let [computed (apply f args)]
                (when (condition-fn computed)
                  (.put ^Cache cache key computed))
                computed))))
      (fn [& args]
        (.get ^LoadingCache cache (or args []))))))

(defmacro defcached
  "Creates a function which uses Caffeine Loading Cache under the hood.
  Function declaration is similar to defn:
  - name (symbol) with optional meta
  - args (vector)
  - caching key (vector) with optional meta containing cache options (see `cached` for more details)
  - body

  Example:
  (defcached customer-lifetime-value [db date {:customer/keys [id]}]
   ^{:expire 86400 :refresh 36000}
   [db date id]
   (query-customer-ltv db date id))
  "
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
